package studio.mises.countdown;

import java.time.LocalDate;
import java.time.YearMonth;

final class CountdownDates {
    static final int ONCE = 0;
    static final int MONTHLY = 1;
    static final int YEARLY = 2;

    static LocalDate next(LocalDate anchor, int repeat, LocalDate today) {
        if (repeat == ONCE) {
            return anchor.isBefore(today) ? null : anchor;
        }

        int step = repeat == MONTHLY ? 1 : 12;
        YearMonth month = repeat == MONTHLY
                ? YearMonth.from(today)
                : YearMonth.of(today.getYear(), anchor.getMonth());

        // Xiaomi skips months and years that lack the original day of month.
        while (true) {
            if (month.isValidDay(anchor.getDayOfMonth())) {
                LocalDate candidate = month.atDay(anchor.getDayOfMonth());
                if (!candidate.isBefore(today)) {
                    return candidate;
                }
            }
            month = month.plusMonths(step);
        }
    }
}
