# 小工具 bitmap（P5-4，V3 靜態一張）

Robolectric 量測，不是實機。`RemoteViews.estimateMemoryUsage()` 是系統檢查上限用的數字（RemoteViews 裡實際放的圖）；Parcel 是 Robolectric 裡寫進 Parcel 的大小（實機上大圖可能走共享記憶體，不能當成傳輸量）。

| 密度 | 4 版面合計 | 其中圖片張數 | Parcel | 只送 TALL（Android 8–11） | 一次更新畫幾張 |
|---|---|---|---|---|---|
| mdpi | 37.9 KB | 4 | 45.0 KB | 12.0 KB | 4 |
| hdpi | 85.5 KB | 4 | 92.7 KB | 27.0 KB | 4 |
| xhdpi | 152.0 KB | 4 | 159.1 KB | 48.0 KB | 4 |
| xxhdpi | 342.2 KB | 4 | 349.3 KB | 108.0 KB | 4 |
| xxxhdpi | 342.2 KB | 4 | 349.3 KB | 108.0 KB | 4 |
