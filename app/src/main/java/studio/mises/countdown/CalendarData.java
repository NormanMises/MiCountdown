package studio.mises.countdown;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.ExtendedProperties;
import android.provider.CalendarContract.Instances;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class CalendarData {
    private static final String COUNTDOWN_TYPE = "9";
    private static final String COUNTDOWN_INFO = "key_countdown_info";
    private static final String[] EVENT_COLUMNS = {
            Events._ID, Events.TITLE, Events.DTSTART, Events.ALL_DAY, Events.RRULE, Events.RDATE
    };
    private static final String[] PROPERTY_COLUMNS = {
            ExtendedProperties.EVENT_ID, ExtendedProperties.VALUE
    };
    private static final String[] INSTANCE_COLUMNS = {
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY
    };
    private static final int INSTANCE_LOOKAHEAD_YEARS = 8;

    record Occurrence(long eventId, String title, LocalDate date) {
        Occurrence {
            if (title == null || title.isEmpty()) {
                title = "（无标题）";
            }
        }
    }

    static Occurrence nearest(ContentResolver resolver, LocalDate today, ZoneId zone) {
        Occurrence nearest = null;
        Set<Long> standardRecurring = new HashSet<>();
        try (Cursor events = resolver.query(Events.CONTENT_URI, EVENT_COLUMNS,
                Events.HAS_EXTENDED_PROPERTIES + "=? AND " + Events.DELETED + "=0 AND ("
                        + Events.STATUS + " IS NULL OR " + Events.STATUS + "<>?)",
                new String[]{COUNTDOWN_TYPE, Integer.toString(Events.STATUS_CANCELED)}, null)) {
            if (events == null) {
                throw new IllegalStateException("日历不可读取");
            }
            if (!events.moveToFirst()) {
                return null;
            }

            Map<Long, CountdownInfo> countdowns = readCountdownInfo(resolver);
            do {
                long id = events.getLong(0);
                CountdownInfo info = countdowns.get(id);
                if (info != null) {
                    LocalDate anchor = dateAt(info.timeMillis(), zone);
                    nearest = earlier(nearest, id, events.getString(1),
                            CountdownDates.next(anchor, info.repeat(), today), today);
                } else if (hasContent(events.getString(4)) || hasContent(events.getString(5))) {
                    standardRecurring.add(id);
                } else if (!events.isNull(2)) {
                    ZoneId eventZone = events.getInt(3) != 0 ? ZoneOffset.UTC : zone;
                    nearest = earlier(nearest, id, events.getString(1),
                            dateAt(events.getLong(2), eventZone), today);
                }
            } while (events.moveToNext());
        }

        if (standardRecurring.isEmpty()) {
            return nearest;
        }

        // A native Xiaomi countdown carries its own repeat rule. CalendarContract repeats
        // are expanded by the provider for records without Xiaomi's countdown metadata.
        LocalDate limit = today.plusYears(INSTANCE_LOOKAHEAD_YEARS);
        if (nearest != null && nearest.date().isBefore(limit)) {
            limit = nearest.date();
        }
        return nearestInstance(resolver, today, limit, zone, standardRecurring, nearest);
    }

    private static Map<Long, CountdownInfo> readCountdownInfo(ContentResolver resolver) {
        Map<Long, CountdownInfo> countdowns = new HashMap<>();
        try (Cursor properties = resolver.query(ExtendedProperties.CONTENT_URI, PROPERTY_COLUMNS,
                ExtendedProperties.NAME + "=?", new String[]{COUNTDOWN_INFO}, null)) {
            if (properties != null) {
                while (properties.moveToNext()) {
                    CountdownInfo info = CountdownInfo.parse(properties.getString(1));
                    if (info != null) {
                        countdowns.put(properties.getLong(0), info);
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w("MiCountdown", "Countdown metadata unavailable", e);
        }
        return countdowns;
    }

    private static Occurrence nearestInstance(ContentResolver resolver, LocalDate today,
            LocalDate limit, ZoneId zone, Set<Long> eventIds, Occurrence nearest) {
        Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
        // Cover both UTC dates for all-day events and local dates for timed events.
        ContentUris.appendId(uri, today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        ContentUris.appendId(uri, limit.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        try (Cursor instances = resolver.query(uri.build(), INSTANCE_COLUMNS,
                Events.HAS_EXTENDED_PROPERTIES + "=?", new String[]{COUNTDOWN_TYPE}, null)) {
            if (instances == null) {
                throw new IllegalStateException("重复日程不可读取");
            }
            while (instances.moveToNext()) {
                long id = instances.getLong(0);
                if (eventIds.contains(id) && !instances.isNull(2)) {
                    ZoneId eventZone = instances.getInt(3) != 0 ? ZoneOffset.UTC : zone;
                    LocalDate date = dateAt(instances.getLong(2), eventZone);
                    if (!date.isAfter(limit)) {
                        nearest = earlier(nearest, id, instances.getString(1), date, today);
                    }
                }
            }
        }
        return nearest;
    }

    private static LocalDate dateAt(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }

    private static Occurrence earlier(Occurrence current, long id, String title,
            LocalDate date, LocalDate today) {
        if (date == null || date.isBefore(today)) {
            return current;
        }
        if (current == null || date.isBefore(current.date())
                || (date.equals(current.date()) && id < current.eventId())) {
            return new Occurrence(id, title, date);
        }
        return current;
    }

    private static boolean hasContent(String value) {
        return value != null && !value.isEmpty();
    }

    private record CountdownInfo(long timeMillis, int repeat) {
        static CountdownInfo parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(value);
                int repeat = json.optInt("repeatType", CountdownDates.ONCE);
                if (repeat < CountdownDates.ONCE || repeat > CountdownDates.YEARLY) {
                    return null;
                }
                return new CountdownInfo(json.getLong("timeMillis"), repeat);
            } catch (JSONException e) {
                return null;
            }
        }
    }
}
