# CoffeeBeanCellar

个人咖啡豆库存管理 Android 原生 MVP。

## 功能

- 咖啡豆图鉴：记录豆名、烘焙商、产地、处理法、烘焙度、风味标签、规格和价格。
- 库存管理：记录剩余克数、开封日期、烘焙日期和赏味状态。
- 冲煮记录：记录器具、粉量、水量、研磨度、水温、时间、评分和备注。
- 消耗统计：查看总库存、本月消耗、总杯数、平均评分和复购候选。
- 提醒中心：提示养豆完成、赏味尾段、开封过久和低库存。

## 构建

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```
