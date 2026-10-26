# 小工具 bitmap 基準（P5-1，舊火柴人，換 V3 之前）

Robolectric 量測（Android 35），不是實機。密度最多算到 3x（`CalendarWidget.setStickman`），所以 xxxhdpi 跟 xxhdpi 一樣。

- 像素估算：寬 × 高 × 4 bytes × 張數，公式算的。
- RemoteViews 估算：`RemoteViews.estimateMemoryUsage()`，系統檢查上限時用的就是這個數字。
- Parcel 大小：Robolectric 裡把 RemoteViews 寫進 Parcel 的大小；實機上大 bitmap 可能走共享記憶體，不能當成實際傳輸量。

| 密度 | 版面 | 小人 dp | 每張 px | 張數 | 像素估算 | 其中輪播多出來的（5/6） | RemoteViews 估算 | Parcel 大小 |
|---|---|---|---|---|---|---|---|---|
| mdpi | COMPACT | 36 | 36×48 | 6 | 40.5 KB | 33.8 KB | 40.5 KB | 45.1 KB |
| mdpi | MEDIUM | 44 | 44×58 | 6 | 59.8 KB | 49.8 KB | 59.8 KB | 64.4 KB |
| mdpi | WIDE | 42 | 42×56 | 6 | 55.1 KB | 45.9 KB | 55.1 KB | 59.7 KB |
| mdpi | TALL | 48 | 48×64 | 6 | 72.0 KB | 60.0 KB | 72.0 KB | 76.6 KB |
| hdpi | COMPACT | 36 | 54×72 | 6 | 91.1 KB | 75.9 KB | 91.1 KB | 95.7 KB |
| hdpi | MEDIUM | 44 | 66×88 | 6 | 136.1 KB | 113.4 KB | 136.1 KB | 140.7 KB |
| hdpi | WIDE | 42 | 63×84 | 6 | 124.0 KB | 103.4 KB | 124.0 KB | 128.6 KB |
| hdpi | TALL | 48 | 72×96 | 6 | 162.0 KB | 135.0 KB | 162.0 KB | 166.6 KB |
| xhdpi | COMPACT | 36 | 72×96 | 6 | 162.0 KB | 135.0 KB | 162.0 KB | 166.6 KB |
| xhdpi | MEDIUM | 44 | 88×117 | 6 | 241.3 KB | 201.1 KB | 241.3 KB | 245.9 KB |
| xhdpi | WIDE | 42 | 84×112 | 6 | 220.5 KB | 183.8 KB | 220.5 KB | 225.1 KB |
| xhdpi | TALL | 48 | 96×128 | 6 | 288.0 KB | 240.0 KB | 288.0 KB | 292.6 KB |
| xxhdpi | COMPACT | 36 | 108×144 | 6 | 364.5 KB | 303.8 KB | 364.5 KB | 369.1 KB |
| xxhdpi | MEDIUM | 44 | 132×176 | 6 | 544.5 KB | 453.8 KB | 544.5 KB | 549.1 KB |
| xxhdpi | WIDE | 42 | 126×168 | 6 | 496.1 KB | 413.4 KB | 496.1 KB | 500.7 KB |
| xxhdpi | TALL | 48 | 144×192 | 6 | 648.0 KB | 540.0 KB | 648.0 KB | 652.6 KB |
| xxxhdpi | COMPACT | 36 | 108×144 | 6 | 364.5 KB | 303.8 KB | 364.5 KB | 369.1 KB |
| xxxhdpi | MEDIUM | 44 | 132×176 | 6 | 544.5 KB | 453.8 KB | 544.5 KB | 549.1 KB |
| xxxhdpi | WIDE | 42 | 126×168 | 6 | 496.1 KB | 413.4 KB | 496.1 KB | 500.7 KB |
| xxxhdpi | TALL | 48 | 144×192 | 6 | 648.0 KB | 540.0 KB | 648.0 KB | 652.6 KB |

## 一次更新的總量

Android 12 以上一次送 4 種版面；Android 8–11 只送放得下的那一種（表中最後一欄列最大的 TALL）。

| 密度 | Android 12+ 像素估算（4 版面 × 6 張） | 同上但只送 1 張（靜態） | Android 12+ RemoteViews 估算 | Android 12+ Parcel 大小 | Android 8–11 單一版面 RemoteViews 估算 |
|---|---|---|---|---|---|
| mdpi | 227.4 KB | 37.9 KB | 227.4 KB | 240.1 KB | 72.0 KB（TALL） |
| hdpi | 513.3 KB | 85.5 KB | 513.3 KB | 525.9 KB | 162.0 KB（TALL） |
| xhdpi | 911.8 KB | 152.0 KB | 911.8 KB | 924.4 KB | 288.0 KB（TALL） |
| xxhdpi | 2053.1 KB | 342.2 KB | 2053.1 KB | 2065.7 KB | 648.0 KB（TALL） |
| xxxhdpi | 2053.1 KB | 342.2 KB | 2053.1 KB | 2065.7 KB | 648.0 KB（TALL） |

## App 日曆頁（同一個 build，TALL 版面放大）

xxhdpi、411dp 寬：每張 181×241 px × 6 張 = 1022.4 KB（像素估算），RemoteViews 估算 1022.4 KB。只在 App 裡用，不經過小工具的傳送。

## 參考：系統上限

AOSP `AppWidgetServiceImpl` 把一次更新的 bitmap 上限設成螢幕寬 × 高 × 6 bytes（1.5 倍全螢幕 ARGB）；1080×2400 約 15187.5 KB。這是讀原始碼得到的規則，沒有在實機上量。
