# Wavelet App Logos & Icons

Centralized collection of all logos, icons, banners, and launcher graphics across platforms for **Wavelet**.

> **🌟 Production Ready Assets**: Head over to the **[`final/`](./final/)** subfolder for the curated, definitive suite of vector SVGs, multi-resolution app icons, and transparent brand marks.

---

## 📁 Directory Structure Overview

```text
app-logos/
├── final/                              # ⭐ FINAL CURATED LOGO SUITE
│   ├── wavelet-logo.svg                # Master vector logo (squircle dark background + red mark)
│   ├── wavelet-logo-mark-red.svg       # Standalone red mark vector (transparent bg)
│   ├── wavelet-logo-mark-white.svg     # Standalone white mark vector (transparent bg)
│   ├── wavelet-favicon.svg             # Web favicon vector
│   ├── wavelet-icon-1024.png           # 1024x1024 master app icon PNG
│   ├── wavelet-icon-512.png            # 512x512 Play Store icon PNG
│   ├── wavelet-icon-256.png            # 256x256 web/social icon PNG
│   ├── wavelet-icon-128.png            # 128x128 extension/UI icon PNG
│   ├── wavelet-mark-red-1024.png       # 1024x1024 transparent red mark PNG
│   ├── wavelet-mark-white-1024.png     # 1024x1024 transparent white mark PNG
│   ├── wavelet-mark-red-512.png        # 512x512 transparent red mark PNG
│   ├── wavelet-mark-white-512.png      # 512x512 transparent white mark PNG
│   ├── wavelet-app-icon.icns           # macOS native icon bundle
│   ├── wavelet-app-icon.ico            # Windows native icon bundle
│   ├── wavelet-tv-banner.png           # Android TV banner (1024x576 PNG)
│   ├── wavelet-logo-original.png       # Original showcase image (1254x1254)
│   └── README.md                       # Guide & specifications for final assets
│
├── Logo.png                            # Main brand logo (1254x1254)
├── Logo.svg                            # Brand logo SVG (red gradient bg)
├── LogoTransparent.svg                 # Transparent brand logo SVG
├── logo-dark-background.svg            # Wavelet logo SVG with dark rounded bg (#0D0D0F)
├── logo-mark.svg                       # Pure Wavelet vector mark
├── desktop-AppIcon-1024.png            # Desktop App icon (1024x1024 PNG)
├── desktop-AppIcon.icns                # macOS application icon bundle
├── desktop-AppIcon.ico                 # Windows application icon bundle
├── playstore-icon-512.png              # Google Play Store listing icon (512x512 PNG)
├── android-launcher-xxxhdpi.webp       # Android standard launcher icon (192x192)
├── android-launcher-round-xxxhdpi.webp # Android round launcher icon (192x192)
├── web-favicon.svg                     # Web favicon
├── web-extension-128.png               # Web extension icon (128x128 PNG)
├── wearos-launcher-xxhdpi.webp         # WearOS launcher icon
├── tv-banner.png                       # Android TV banner (320x180 PNG)
│
├── brand/                              # Core Brand Assets
├── desktop/                            # Desktop Application Icons
├── android/                            # Android App & Store Icons
│   └── glass-themes/                   # Dynamic Glass Home-Screen Icons
├── web/                                # Web & Browser Extension Icons
├── wearos/                             # WearOS Smartwatch Assets
└── tv/                                 # Android TV Assets
```

---

## 📋 Asset Catalog & Source Mapping

| Target Asset | Format | Resolution | Original Source Path | Description |
| :--- | :--- | :--- | :--- | :--- |
| `final/wavelet-logo.svg` | SVG | Vector (1024x1024) | `desktopApp/.../drawable/logo.svg` | Master logo with squircle bg |
| `final/wavelet-logo-mark-red.svg` | SVG | Vector (1024x1024) | `desktopApp/.../drawable/logo_mark.svg` | Brand red mark on transparent bg |
| `final/wavelet-logo-mark-white.svg` | SVG | Vector (1024x1024) | `web/app/src/assets/logo-mark.svg` | White mark on transparent bg |
| `final/wavelet-icon-1024.png` | PNG | 1024 x 1024 | `desktopApp/packaging/icons/AppIcon.png` | Master high-res icon |
| `final/wavelet-icon-512.png` | PNG | 512 x 512 | `app/src/main/ic_launcher-playstore.png` | Play Store icon |
| `final/wavelet-app-icon.icns` | ICNS | Multi-res | `desktopApp/packaging/icons/AppIcon.icns` | macOS packaging icon |
| `final/wavelet-app-icon.ico` | ICO | Multi-res | `desktopApp/packaging/icons/AppIcon.ico` | Windows packaging icon |
| `final/wavelet-tv-banner.png` | PNG | 1024 x 576 | `tv/src/main/res/drawable-xhdpi/tv_banner.png` | Android TV banner |
