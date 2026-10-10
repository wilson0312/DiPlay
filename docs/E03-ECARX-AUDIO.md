# E03 原车导航 / 音乐独立音量

日期：2026-10-10。原车能力与两套代码路径已核查；首批音频源码状态见[实施记录](E03-ECARX-IMPLEMENTATION.md)。本车播放/按键验收未完成。

## 1. 修正结论

本车原厂具备独立 MUSIC / NAVI stream；目标是让 CarPlay 使用现有两路，不是新增音量组或仅提供两组应用增益。
此前遗漏本车历史快照及 carlito 的非零 legacy 构造分支，过于保守的系统能力判断在此更正。
软件代码、原车能力、实际播放和实体键效果分别记录，后两项仍需验收。

## 2. 原车证据（仅公开脱敏结论）

| 项目 | 本车历史快照 / 本机系统代码 | 结论 |
| --- | --- | --- |
| 音乐 | STREAM_MUSIC；native stream03；speaker index8 | 音乐有自己的系统音量状态 |
| 导航 | STREAM_NAVI；native stream12；speaker index16 | 导航状态不同于音乐 |
| 曲线 | MUSIC03 与 NAVI12 各有音量曲线，范围0–39 | 并非只在 App 中分滑块 |
| ECARX 别名 | AudioService 实际使用 ECARX 表：3→3、12→12 | 导航没有别名到音乐 |
| 对照 stream | radio13→3；beep14→1 | 不是全部扩展 stream 都独立；本车14不是导航 |
| 系统选择 | getActiveStreamType 在 platform1/default 建议分支可优先活跃导航12 | 有原厂导航调节路径，非所有实体键已验证 |

证据来自用户车辆2026-10-08的安装前音频快照及已保存 services.vdex，不是两 App 播放验收记录。
系统数组由原始 fill-array-data payload 解出；quickened 字段/调用未解析处不猜名称。
原始日志、APK、系统文件和车辆/手机标识保留在仓库外，只公开结论。
音量范围取当前系统 API；不把这次快照的39写成所有 E03 固件通用上限。

## 3. 三种编号不能混用

| 参数 | 含义 | 本车相关值 |
| --- | --- | --- |
| Android usage | 音频用途，交给 ROM 策略映射 | 标准媒体1、标准导航12 |
| legacy/native stream | 原车系统音量对象 | 音乐3、导航12 |
| output device | 输出设备选择 | speaker、蓝牙或USB等，与 stream 不同 |

导航 usage12 恰巧和本车 stream12 同值，不能据此认定用途映射已经证明；媒体 usage1 / stream3 更清楚体现区别。
本次没有本车 AudioAttributes / native 属性映射实现或活跃轨道证据，usage12→native12不标为已验证。
独立音量允许在同一组车载扬声器输出，不要求两个物理设备。

## 4. 酷安样本：用途分流

固定样本：CarPlay1.3.2/1302，SHA256见[依据文档](E03-ECARX-EVIDENCE.md)。APK不分发。

```text
audioType + payloadType → AudioChannelMapper
  → 按 AudioStreamKey 独立 renderer
  → 分别加载媒体/导航枚举与自定义 usage
  → AudioAttributes.setUsage/setContentType
  → AudioTrack.Builder → ROM 音频策略
```

已提取的真实 DEX 指令确认：

- 未保存配置时：媒体 MEDIA=usage1/content MUSIC2；导航 NAVIGATION=usage12/content SPEECH1。
- media_audio_usage / navigation_audio_usage 默认-1，不覆盖枚举用途；自定义非负值优先。
- 自定义界面称“声道”，实际上 setUsage；填媒体3会成为用途3，不是指定音乐stream3。
- 建轨只走带属性 Builder，没有直接 legacy stream12 构造分支。
- music duck 调 AudioTrack.setVolume(0.3/1.0)，它是临时增益，不是独立音量机制。

这套实现确实把两类声音分开交给车机，不是只有两组设置文案。使用效果取决于实际用途映射与本车音量调节路径。

## 5. carlito：用途或明确 stream

固定版本：v0.2.14 `f11d9c9…`；main/v0.2.16 `505d34a…`。

- [默认数值][prefs]：媒体0、导航0；导航仅在新键缺失时继承旧保存值。0是用途模式，不是静音。
- [厂商用途][factory]：反射 AUDIO_USAGE_CP_*，其次原厂 JSON，缺值使用标准用途；它位于公开主应用，不经 GD。
- [非零数值][track014]：直接 AudioTrack(streamType, …)，而不是只尝试标准范围 setLegacyStreamType。
- [设备优先规则][override]：选择 preferred output 会让 override=0，覆盖数值路由。先看实际生效路径，不只看保存的12。
- ROM拒绝legacy时已有 usage 回退；E03 明确路由模式不得把这一回退算成功。
- [v0.2.14发布][release]明确“导航独立输出”，但没有 E03 精确数值或本车实体键验收。

因此本车可明确选择**媒体 stream3、导航 stream12**，以 ROM 接受并实际输出为验收条件；不照搬 BYD导航14。
默认厂商检测还有配置/已安装包替代条件，缺少 E03 型号字样不等于不支持；本车实际命中结果尚无文件证据。

## 6. 输出与音量键是两项功能

v0.2.14 的公开 WheelKeyService没有应用内导航 setStreamVolume 路径；独立输出仍可由 OEM 调节。
main/v0.2.16 与本分支原版基线已含 [NavigationPlayback + WheelKeyService][wheel]：

```text
实际播放导航 PCM + 明确 legacy stream
  → 已授权键服务收到实体键
  → setStreamVolume(导航stream, 新值, FLAG_SHOW_UI)
  → 回读确认并配对消费按下/重复/抬起
```

它默认关闭，仅在导航活跃、会话有效、无优先电话/助手时处理；用途模式、被拒绝回退、非明确路由不提供同样键能力。
[原版说明][wheel-doc]的已测实例是 BYD唐导航14，不是 E03。扩展stream12的min/max API和本车事件仍需验收。
本车 AudioService也存在导航活跃时选择12的代码，但实际键可能走显式音乐流或其他 OEM 路径，不提前宣布自动切换通过。

## 7. 本分支具体决策

1. **首选 E03 明确路由：音乐3、导航12**。使用现有协议分类、renderer和非零 AudioTrack 接入点，不另写协议或音频解码。
2. `:vehicle-ecarx` 提供具名、带固件/验证状态的 E03 路由配置；改变配置按既有用户重连流程生效。
3. 区分用途模式、E03 stream模式、设备模式；设备冲突显式展示，不悄悄取消stream选择。用途模式不是 E03 stream失败后的自动替代。
4. E03 stream建轨失败、未初始化或实际路由不符，报告具体失败；不自动统一到媒体，不借打印 ready 声明通过。
5. 用户音量读取/写入对应原车stream，由系统保存；应用增益和duck单独管理，不伪装成原车音量。
6. 首先保留系统音量键职责；原厂已正确处理则不双调。应用导航键处理仅在明确选用时复用现有机制，不自动开启无障碍或日志模式。
7. 不监听所有音乐音量变化再强制恢复，不把 BYD最小音量14特例推广到E03导航12。
8. OEM快照已有导航duck；协调位置只选一处生效，不盲目再乘0.2/0.3。电话/助手保留逻辑优先级，物理路由另行验证，不先照抄全部音乐输出。
9. 安装/SDK接入与音频机制分开验收；公开主应用可路由不等于自建签名、主题安装和厂商授权全部通过。

## 8. 验收与本次检查

详见[音频验收](E03-ECARX-ACCEPTANCE.md#6-独立音量与焦点)：实际tracks分别3/12；分别调节且另一套index不动；重连/重启保持；播报中/后实体键对象正确；无双调、双duck；电话/助手/蓝牙单列。
已确认的原车能力不用重复写成猜测，未执行的播放和键测试不记为成功。
此前音频更正提交仅改文档，其原始字节提取检查不等于Android test/lint/build通过。
此前完整CI尝试因Gradle下载DNS失败而未开始测试。本批已准备工作区 JDK 25 / Gradle；最新测试、构建结果在[实施记录](E03-ECARX-IMPLEMENTATION.md)，不沿用此前的环境结论。

[release]: https://github.com/carlito12345/DiPlay/releases/tag/v0.2.14
[prefs]: https://github.com/carlito12345/DiPlay/blob/505d34a2d6995e7194d645e96bbd1a5769135b7a/common/src/main/java/com/shilapi/xcertplay/AirPlayPersistence.kt#L250-L274
[factory]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/shared/src/main/java/com/shilapi/xcertplay/vehicle/GeelyFactoryCarPlay.kt#L38-L102
[track014]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt#L1068-L1110
[override]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt#L1164-L1170
[wheel]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/common/src/main/java/com/shilapi/xcertplay/WheelKeyService.kt#L192-L218
[wheel-doc]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/docs/NAVIGATION_WHEEL_VOLUME.md
