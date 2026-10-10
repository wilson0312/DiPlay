# E03 设计依据：酷安样本与 carlito 对比

日期：2026-10-10。静态代码和公开源码证据不等于实车效果。

## 固定研究版本

| 来源 | 身份 |
| --- | --- |
| 原版上游 / 自建基线 | `6ebb58218f90b0c0853b78013cb8431765ca77bb` |
| carlito v0.2.14 | `f11d9c94bdc0574d132bd4afe65e31778c51144b` |
| carlito main / v0.2.16 核查时 | `505d34a2d6995e7194d645e96bbd1a5769135b7a` |
| 用户酷安 APK | CarPlay 1.3.2 / 1302，`com.ganfandw.carplay` |
| 酷安 APK SHA256 | `a5c25ce74c6605a0082f934634aa0fc462cbe79f828b47b0f87de95bbcd63f3b` |

酷安 APK 不随本仓库分发；未取得可信对应的全部定制源码。
它记录的公开基线 `eae4e7babafb91f2768687061b5f22515b8cf444` 不足以重现其 ECARX 修改。
APK 方法证据来自 Androguard 4.1.3 的真实 DEX 指令，不运行 APK，不导出 SDK 身份值。

## 对比结论

| 项目 | 酷安本地样本 | carlito v0.2.14 | 本分支取舍 |
| --- | --- | --- | --- |
| OEM 接入 | 同 APK 中直接 ECARX 媒体/Input/DIM | 主程序绑定外部 GD，服务端不在公开树 | 单 APK 内部适配，部署授权单列 |
| 地图来源 | iPhone 仪表视频 111 | 同样为仪表视频 111 | 复用原版，不重写协议 |
| 输出窗口 | Presentation + Surface | 目标 Display overlay + Surface | E03 原厂区域 / Presentation 优先，需验证 |
| 模式确认 | DIM 放行和窗口结果分开 | 内容命令接受 + 首帧 + GD ready | 分阶段证据，窗口建立不报总成功 |
| 原导航争用 | 显式服务状态修改、已有恢复记录；服务存在时缺省开 | GD 内部模式进入/恢复实现未知 | 原厂协调优先；暂停默认关且显式选择 |
| 方控 | 媒体中心/Input/KeyEvent/日志，已有去重 | GD 回调/映射/消费状态与去重 | 按操作选主通路，分别验证手机和原播放器 |
| 音频 | 分类/用途可配置；music duck gain 0.3 | 角色/用途/设备可配置；duck gain 0.2 | 系统独立音量单独验收，压低音乐另计 |
| 维护代价 | 全部定制构建来源未核实 | 公开主程序 + 另签名闭源 GD | 原版核心 + 小范围源码适配，不暗中依赖外部桥 |

## 1. carlito：外部桥的准确边界

- [v0.2.14 release][r014] 明确要求车辆数据桥 0.11.24。
- `vehicle-probe` 是 [Android library][probe]，通过 AIDL 绑定 GD 服务，并非可构建的 GD 应用实现。
- 0.2.14 [音频协调文档][audio-doc] 已写 separate private bridge；不是新版才引入。
- [v0.2.16 release 元数据][r016] 提供 `GD-Vehicle-Bridge-0.11.25-FX11.apk`；不能混称为已装的 0.11.24。
- [main 整合说明][integration] 明确 FX11 的闭源单独签名桥，以及新方控/语音行为仍待硬件验证。

桥客户端权限/注册成功不证明 E03 授权、OEM 按键独占或原厂仪表模式切换已成功。
GD 的服务端调用方校验、E03 厂商内部调用顺序未由公开客户端证明。

## 2. 方控：应借鉴的状态与实际限制

[VehicleSteeringClient][steering] 会校验租约、键集合及 intercepted 状态，真实事件数参与 ready。
[CarPlayMediaKeys][keys] 负责规范化、动作触发、跨来源/同 inputId 去重，再走原有发送点。
上游 [controller.sendMediaButton][controller] 的 true 表示发送任务入队，不是手机执行确认。

[main 的被动媒体事件拒绝][passive] 和 [整合说明][integration]指出：日志/广播观察不能阻止原车消费；已有 ECARX 回调也不证明其他 OEM 消费者未响应。
测试中关闭原播放器后方控可用，不写成独占通过。

酷安对应方法证据：

- `ECarXMediaBridge.register/command`：注册原厂媒体源，语义回调发 iAP2/媒体命令。
- `EcarxKeyInterceptor.start`：`Input.create`、动态 IKeyCallback、返回接管键集合及消费结果。
- `CarPlayHostActivity.syncKeyInterception`：按设置、蓝牙、学习键及当前通路选择接管职责。
- `handleMediaKey`：300ms 去重及 `claimTrackChange`，已有 SDK/按键重复处理。
- `syncWheelLogMonitor`：指定模式和已有模板才开启日志监听。

## 3. 飞屏：两套都不是“必然复制中控”

上游 [CarPlayClusterDisplay][cluster] 明确 type 111、iPhone 的 altScreenURLs 和仪表内容 URL。
carlito [地图计划/输出][map]使用第二路视频并分离命令接受、首帧和桥 ready；[投影客户端][projection]持有 owner 生命周期，隐藏/断连释放。
[手势入口][gesture]是三指上滑，不改变视频来源。

酷安对应方法证据：

- `onHostTouch`：三指左滑请求显示，右滑收起，下滑打开设置。
- `prepareClusterPresentation`：目标屏尺寸与 `maps:/car/instrumentcluster/map`。
- `clusterSurfaceListener`：`AndroidMediaSink.setSurface(111, surface)`。
- `ClusterDimController.open`：原厂 `setPresentationToDimSwitch`，并发布结构化导航信息。
- `reassertDisplaySource`：反射 `mPowerOperation.disableScreenProjection(false, 2)`；2 的厂商语义待核对。
- `openClusterProjection$lambda$434$lambda$433`：启用 111/创建窗口后，按 DIM 和窗口结果触发原导航争用处理。
- `ClusterOemNavigationGuard.pause/restoreNow`：同步持久记录、精确原状态恢复及核对，不是没有恢复处理。

手机提供视频，车机决定请求内容、解码出口与原厂显示模式。原导航自身三指实现未被本次源码证明与上述 CarPlay 通路相同。

## 4. 音频：用途、增益与系统音量是三件事

carlito [音频输出][renderer]配置角色、用途和部分 legacy stream；[厂商配置读取][factory]取得原厂用途值，并不授予系统权限。
[焦点协调][focus]在导航活跃时将 MEDIA AudioTrack 增益降至 0.2，不是新增 E03 volume group。

酷安 `AudioChannelMapper` 用 audioType + payloadType 分类；`AudioRenderer.audioAttributes/createTrack` 为媒体和导航分别构建用途并创建轨道。
`AndroidMediaSink.applyMusicDucking` / `AudioRenderer.setDucked` 的实际音乐增益是 0.3/1.0。
已提取的音频核心尚未证明可由用户分别调媒体/导航增益，也没有 E03 原车独立音量组的运行证据。

上游 [导航方控音量说明][wheel-volume]描述已测 BYD 车型的私有 stream 14，并明确不是其他车机兼容承诺。E03 不直接继承该数值或验证结论。
先检查真实路由和音量状态，再决定音量键职责；不通过监听所有媒体音量变化来猜用户意图并强制回改。

## 5. 复用范围与未完成验证

原版现有接入点是 [MediaSink][sink]、[controller][controller]、[ClusterDisplay][cluster]和 [AudioChannelMapper][mapper]。
基线没有 VehiclePlatform/KeySource 命名接口；carlito 的新 owner/projection API 也不是原版现成能力。
移植思想要同时审查会话生命周期，不仅复制窗口文件。

[原版构建说明][build]要求外部提供 standalone 运行认证输入，源码构建与可连接手机不是同一个结论。
此次没有运行任何车机测试，没有实现身份获取、权限修改、模式切换或音频路由修改。
对 E03 的部署、方控独占、地图/原模式恢复和独立音量分别执行 [验收计划](E03-ECARX-ACCEPTANCE.md)。

[r014]: https://github.com/carlito12345/DiPlay/releases/tag/v0.2.14
[r016]: https://api.github.com/repos/carlito12345/DiPlay/releases/tags/v0.2.16
[probe]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/vehicle-probe/build.gradle.kts#L1-L15
[audio-doc]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/docs/AUDIO-COORDINATION.md#L3-L19
[integration]: https://github.com/carlito12345/DiPlay/blob/505d34a2d6995e7194d645e96bbd1a5769135b7a/docs/INTEGRATION-0.2.16-20261009.md#L5-L13
[steering]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/vehicle-probe/src/main/java/com/shilapi/xcertplay/vehicleprobe/VehicleSteeringClient.kt#L32-L138
[keys]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt#L381-L554
[passive]: https://github.com/carlito12345/DiPlay/blob/505d34a2d6995e7194d645e96bbd1a5769135b7a/common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt#L417-L429
[controller]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt#L524-L745
[cluster]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayClusterDisplay.kt#L3-L23
[map]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/common/src/main/java/com/shilapi/xcertplay/VehicleMapProjection.kt#L28-L234
[projection]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/vehicle-probe/src/main/java/com/shilapi/xcertplay/vehicleprobe/VehicleProjectionClient.kt#L36-L90
[gesture]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt#L4602-L4613
[renderer]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/shared/src/main/java/com/shilapi/xcertplay/media/AndroidMediaSink.kt#L1148-L1280
[factory]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/shared/src/main/java/com/shilapi/xcertplay/vehicle/GeelyFactoryCarPlay.kt#L38-L98
[focus]: https://github.com/carlito12345/DiPlay/blob/f11d9c94bdc0574d132bd4afe65e31778c51144b/shared/src/main/java/com/shilapi/xcertplay/media/AudioFocusCoordinator.kt#L127-L196
[wheel-volume]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/docs/NAVIGATION_WHEEL_VOLUME.md
[sink]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayMediaEngine.kt#L15-L35
[mapper]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/shared/src/main/java/com/shilapi/xcertplay/media/AudioChannelMapping.kt#L5-L50
[build]: https://github.com/shihabal3amri/DiPlay/blob/6ebb58218f90b0c0853b78013cb8431765ca77bb/docs/BUILD.md
