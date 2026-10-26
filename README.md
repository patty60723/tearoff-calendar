# 墨丸 V3 — P5-4 Widget V3 驗收素材

- 程式 commit：`f127bd5`
- 模擬器：Emulator acceptance #30（https://github.com/patty60723/tearoff-calendar/actions/runs/40284503759），PASS 41、FAIL 0、WARN 0
- Robolectric：Build APK #70（https://github.com/patty60723/tearoff-calendar/actions/runs/40284503714）

## emulator/（Android 14 模擬器，不是實機）
- `report.txt`：完整驗收紀錄
- `shots/widget-host-*.png`：測試宿主（系統 AppWidgetHost）掛 5 種大小：V3、舊火柴人、密度 320／560
- `shots/launcher-*.png`：真正的 Pixel launcher 桌面：1 放上去 → 2 卸下包袱 → 3 脫下紙帽 → 4 第 5 頁 → 5 下雪 → 6 關 V3（舊火柴人）→ 7 開 V3 → 8 在 App 裡卸下包袱
- `launcher-sequence.png`：上面 8 張裁切並排
- `ui/gfx-widget-*.txt`：小工具 10 秒內的 gfxinfo（V3 vs 舊火柴人）

## robolectric/p5/
- 小尺寸簡化對照（P5-2）、小工具與日曆頁截圖（P5-3）、`bitmap-baseline.md`、`bitmap-v3.md`
