# 最近倒数日（MiCountdown）0.2.1

Android 桌面小部件。只申请读取日历权限。

## 安装和添加

从 [Releases](https://github.com/NormanMises/MiCountdown/releases) 下载 APK。

1. 在小米澎湃 OS 手机上安装 APK，打开「最近倒数日」并授予日历读取权限。
2. 点应用内的「添加到桌面」，根据系统提示确认。若桌面启动器未提供这个操作，双指捏合桌面，打开「小部件」，从「搜索 → 安卓小部件」选择「最近倒数日」。入口名称和位置可能随系统版本变化。
3. 已添加的小部件可通过应用内的「刷新小部件」手动更新。

本项目可以通过 GitHub 侧载分发。澎湃 OS 小部件中心的「安卓小部件」入口用于添加这类本地 APK 所提供的卡片。小米主卡片列表由小米审核和发布流程管理。[小米开发者文档](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664) · [小部件审核与上传指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1588)

小部件显示今天或之后最近的小米倒数日。标题支持多行和自动字号，点击卡片可查看完整标题。它会自动跟随系统深浅色模式。外观使用半透明渐变和柔边高光。支持提供可用尺寸的 Android 12 及以上启动器会收到居中的正方形卡片。

## 倒数日期

只读取 `hasExtendedProperties=9` 的日程。小米倒数日目标日期和重复方式取自 `key_countdown_info`，每月或每年重复时跳过没有原始日期的月份或年份。没有小米扩展数据的标准重复日程使用 Calendar Provider 的实例日期，向前查询最多八年。已过去的一次性倒数日不显示。系统约每 30 分钟请求一次刷新，也可在应用中手动刷新。

## 构建

使用 JDK 17、Gradle 8.9 或更高版本和 Android SDK 35，运行 `gradle :app:assembleDebug`。也可运行项目中的 Build APK 工作流。

## 许可证

[MIT](LICENSE)
