<div align="center">

# TaffyQSL

<p align="center">
  <img src="taffyQSL_noblack.svg" alt="Taffy QSL icon" style="width: 192px" />
</p>
<p><br/></p>
<p align="center">
  <a href="https://github.com/sophiel-meow/TaffyQSL/releases/latest">
    <img src="https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android" alt="下载 APK">
  </a>
</p>

[![license](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
[![kotlin](https://img.shields.io/badge/Kotlin-2.3-orange.svg)](https://kotlinlang.org)
[![android](https://img.shields.io/badge/Android-16-brightgreen.svg)](https://developer.android.com)

[English](README.md) | 简体中文 | [日本語](README.ja.md)

</div>

---

TaffyQSL 是一款 100% 开源、自由、尊重隐私的 Android 业余无线电通联日志软件。  
支持 ADIF 文件管理、QSO 签名及 LoTW 集成，并通过 Android Keystore 提供**硬件级密钥保护**。  
专为重视隐私与安全的业余无线电爱好者设计，TaffyQSL 将您的证书与日志安全保存在本地设备上。

## 功能特性

- 创建、编辑并导出 ADIF 文件
- 兼容 LoTW 的 QSO 签名
- 硬件级私钥保护（取决于设备支持情况）
- 自然语言 QSO 解析器，快速便捷地记录通联
- 支持卫星通联及 DXCC 实体
- 可自定义日期和时间显示格式

## 应用截图

<p align="center">
  <img src="metadata/en-US/images/phoneScreenshots/1.png" width="200"/>
  <img src="metadata/en-US/images/phoneScreenshots/2.png" width="200"/>
  <img src="metadata/en-US/images/phoneScreenshots/3.png" width="200"/>
  <img src="metadata/en-US/images/phoneScreenshots/4.png" width="200"/>
</p>

## 隐私与安全

- **密钥在本地生成并存储**于 Android Keystore 中
- **私钥本地储存并受到Android Keystore保护**
- **私钥不会被外部服务获取**
- **备份导出功能始终由用户自主掌控**
- **无数据收集，无追踪**
- **100% 自由开源**
- 在与 LoTW 交互的同时，全力保护您的隐私

## 安装方式

- **最低 Android 版本：** Android 11（API 30）
- 从 [Releases](https://github.com/sophiel-meow/TaffyQSL/releases) 页面下载最新 APK
- F-Droid 版本即将上架

## 从源码构建

TaffyQSL 使用 Kotlin 编写，采用 Gradle Kotlin DSL 构建系统。
```bash
git clone git@github.com:sophiel-meow/TaffyQSL.git
cd TaffyQSL
./gradlew assembleDebug
```

**构建要求**

- JDK 21
- Android Studio（最新稳定版）
- Android SDK，API 36 或更高

## 免责声明

- 本软件按「现状」提供，不附带任何形式的保证。
- 使用风险由用户自行承担。
- LoTW 是美国无线电中继联盟（ARRL）的注册商标。
- TaffyQSL 是独立项目，与 ARRL 无关联，亦未获其背书。
- 硬件级加密功能取决于设备的具体能力。

## 参与贡献

欢迎贡献！

- 如有功能建议或问题反馈，请提交 Issue
- 欢迎提交 Pull Request 修复 Bug 或改进功能
- 讨论、Issue 及贡献支持使用**英文（首选）、中文或日文**

## 待办事项

- [ ] F-Droid 发布优化
- [ ] PIN 码与生物识别支持
- [ ] 更多卫星支持
- [ ] 备份功能
- [ ] QRZ.com 在线查询 / 同步
- [ ] 统计功能

## 许可证

Copyright (C) 2026 Sophiel (BG4KNN)

TaffyQSL 是依据 **GNU 通用公共许可证 v3.0** 发布的自由软件。  
您可以在 GPL 条款下自由使用、修改和再发行本软件。

🄯 Copyleft 2026 Sophiel (BG4KNN)

本项目为独立项目，与 ARRL 及 Logbook of the World 无任何关联或背书。  
所有商标归其各自所有者所有。

## Credits

Designed by Sophiel & Alice

Inspiration:
- [TrustedQSL](https://sourceforge.net/projects/trustedqsl/)
- [X-QSL](https://gitee.com/yuzhenwu/x-qsl-amateur-radio-adif-tool)
