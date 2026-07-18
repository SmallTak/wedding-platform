# 糖诗·美学品牌资产

本目录是官网、工作台和图片水印共用的品牌资产源。

- `mark-dark.svg`：浅色背景方形标志。
- `mark-light.svg`：深色或照片背景方形标志。
- `mark-mono.svg`：单色方形标志。
- `favicon.svg`：浏览器方形标志。
- `lockup-dark.svg`：浅色背景横版组合标。
- `lockup-light.svg`：深色背景横版组合标。
- `watermark.svg`：预览图水印源文件。
- `watermark.png`：后端实际合成使用的透明位图，由 `watermark.svg` 导出。

正式中文名必须写作“糖诗·美学”，英文 `TANGSHI AESTHETICS` 仅作为辅助信息。
执行 `deploy/scripts/sync-brand-assets.sh` 将运行时副本同步到三个独立构建模块。
