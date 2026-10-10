# E03 / ECARX 实施记录

日期：2026-10-10。分支：`codex/e03-ecarx`。

**首批音频源码已实现；完整 Android 构建、设置界面验收和实车验收未完成。方控、仪表和 TBT 尚未实现。**

本页是实施状态的记录；[设计](E03-ECARX-DESIGN.md)、[音频依据](E03-ECARX-AUDIO.md)与[实车验收表](E03-ECARX-ACCEPTANCE.md)仍分别负责决策、证据与验证要求。

## 1. 本批内容

| 项目 | 实现 | 验证状态 |
| --- | --- | --- |
| 单 APK Module | 新增 Android library `:vehicle-ecarx`，由 `:common` 依赖，不增加独立安装包 | Gradle 接线已写入；最终 APK 待构建 |
| 明确选择车型音频模式 | 默认原版；用户显式选择 E03 Android 9；不凭品牌自动启用 | 模式选择和存储测试通过 |
| E03 独立路由 | 音乐 legacy stream 3，导航 legacy stream 12 | 源码接线与严格建轨测试通过；本车 native tracks 未测 |
| 严格建轨 | 构造拒绝、未初始化、Java 报告 stream 不一致均失败；释放被拒轨道，无 usage 重试 | 拒绝、清理、重复释放及错误传播测试通过 |
| 系统压低音乐 | E03 的 CAN_DUCK 不再乘应用 0.2；瞬时焦点丢失仍遵循原有静音选项 | Android 9 焦点测试通过；实际 OEM 压低与恢复未测 |
| 设置 | 高级页提供实验性模式；保存不打断会话，显示既有重连提示；E03 下音频页展示固定路由而非失效的编辑控件 | 七种语言已补齐；实际 UI 测试与截图待执行 |
| 原版兼容 | 切回原版仍使用先前保存的自定义路由；其明确的 ROM usage 兼容策略保留 | 配置保留及 legacy 兼容单元测试通过 |
| 方控、DIM、TBT、第二路视频 | 本批没有新增实现 | 未实现，不宣称已支持 |

先实施音频，是因为本车 3/12 音量对象已被核实，且可复用现有协议分类和播放入口。厂商方控/仪表的授权、注册与原车独占仍是另外的实现和验收事项。

## 2. 实际接入位置

```text
CarPlayHostActivity
  ├─ EcarxAudioPreferences.load()：读取明确选择的模式
  ├─ EcarxAudioMode.routing()：原版配置或 E03 3/12 配置
  └─ AndroidMediaSink：会话开始时捕获不可变配置
       ├─ 原有 AudioChannelMapper：分类音乐、导航、通话、助手
       ├─ AndroidAudioTrackFactory：严格或原版兼容建轨策略
       ├─ AndroidLegacyTrackFactory：真实 Android 平台调用
       ├─ StrictLegacyAudioTrack：确认初始化/报告 stream，失败释放
       └─ AudioFocusCoordinator：应用或系统负责 CAN_DUCK
```

新模式不改写上游保存的数字；新会话读取模式，运行中的旧会话保持原配置。
Phone / Assistant 保持用途路由，既有显式扬声器通话策略不被重写。
没有新的音量键服务；`NavigationPlayback` 仍接收成功创建的 legacy stream，既有导航音量键功能仅在用户原先明确开启时工作。

`AudioTrack.getStreamType()` 的一致性检查是 Java 可观察的检查，不替代本车 AudioFlinger / audio policy 的实际路由证据。
Android 9 的 focus attributes 仍标为 configured；不假装已读回 Android 10 才公开的轨道属性。
原版底层没有 carlito 的 preferred-device 设置，本批也没有新增会暗中覆盖 stream 12 的设备优先路径。

## 3. 设置操作

1. 打开“设置 → 高级 → E03 独立音量（实验性）”。
2. 选择“E03（3 / 12）”，点击保存；取消不会修改模式。
3. 设置对下一次 CarPlay 连接生效。有运行中的连接时，只显示既有“立即重连”入口，不自行断开。
4. 音频页显示下一连接的固定路由。切回“原版路由”后，原先保存的数字仍在。

启用不自动授予权限，不启用无障碍/日志模式，不接管音量键，不改系统音频策略，也不改变原车导航的启用状态。
路由错误进入本会话音频诊断并保留错误类型、请求 stream、失败阶段和已报告 stream；清理失败附在原异常上。
主程序的安装身份、CarPlay 运行认证输入与 ECARX SDK 权限仍分别验收，见[部署条件](E03-ECARX-DESIGN.md#2-三类部署条件互不替代)。

## 4. 已执行检查

- 使用官方 Kotlin 2.2.10、JDK 17、官方 Android 37 接口，局部编译本批音频 Adapter、车型模式、存储及其真实上游依赖：通过。这不是完整 APK 构建。
- 纯 JVM 核心测试：**25 项通过**，包括配置、3/12、错误与清理、平台 API 失效时释放资源、原版 legacy 兼容及原有角色分类。
- Robolectric 4.17 / Android 9（API 28）运行测试：**18 项通过**，包括焦点行为、系统压低归属、真实策略的严格/兼容分支与配置存储。
- 该次运行明确只选 API 28；既有焦点测试的 API 25 / 33 不记为已执行。
- 音乐轨道测试显式提供 Robolectric 未执行的 JNI stream 字段填充；拒绝测试使用注入的失败操作。这些测试验证代码，不模拟产品中的成功，也不作为实车路由通过证据。
- 七种语言的实际 `SettingsTranslationsTest`：**1 项通过**，逐一检查全部设置键及格式占位符。本批已执行的测试共 **44 项通过**。
- Git diff 空白、新实现文件行数、资源 XML 及公开树检查：**通过**；上游原有大型 Activity / sink 只做接线和按职责抽出音频代码，没有整份重写。
- 已使用工作区隔离的 JDK 25.0.2、Gradle 9.5.0、SDK platform 37 和 build-tools 36 执行根 AGENTS 的完整 test / lint / assemble 命令（含新模块），设置 60 秒硬超时。
- 完整检查实际 **exit 1，4 秒失败**：配置阶段报告 NDK `28.2.13676358` 尚未安装且许可未接受。测试、lint、assemble 任务未开始；没有生成 APK。这不是源码编译错误的结论，也不是完整构建通过。
- 设置 UI 的取消/保存/旧控件隐藏测试，以及紧凑/完整/阿拉伯语大字体原生渲染测试已加入源码；未执行前不把截图生成或布局验收标为通过。

## 5. 本批未发生的车辆操作

没有连接 ADB、安装或卸载应用、替换主题、暂停原导航、修改白名单、导出车辆属性或发布 APK。
仓库中没有添加原始用户 APK、原始车辆日志、厂商 AppKey、认证输入、签名私钥或共享 system UID。
自建 APK 的实车安装和连接尚未验收；上述软件测试不等于[音频 A01–A14](E03-ECARX-ACCEPTANCE.md#6-独立音量与焦点)通过。
