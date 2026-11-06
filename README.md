<p align="center">
  <img src="docs/images/jyvr-logo.png" width="240" alt="JYVR">
</p>

# TaCZ-JYVR

Minecraft 1.20.1 的 TaCZ VR 修改版，配合 Forge 与 Vivecraft 使用。当前版本：**0.1.0**。

通过 VR 手柄操作枪械：举枪瞄准、拆装弹匣、逐发装填，并按枪械的动作类型操作枪机、护木或其他可动部件。

## 功能

- 手柄跟踪与双手持枪交互。
- 腰侧取弹、手动拆装弹匣及逐发装填。
- 依据枪械模型和动作数据驱动换弹交互。
- 镜内放大视野，包含世界、生物与其他玩家的渲染。
- 弹壳弹出与存在时间设置，默认保留 5 秒。
- 开火震动与逐发装填音效。
- 可切换的碰撞箱及交互区域显示。

不同枪包的模型、动画和坐标可能不同。自定义枪包的交互位置可能需要单独校准。

## 安装

1. 准备 Minecraft 1.20.1、Forge 和 Vivecraft。本项目的构建目标为 Forge 47.3.19，适配参考版本为 Vivecraft 1.3.15。
2. 从本仓库 Releases 下载 `TaCZ-JYVR-0.1.0.jar`，放入当前游戏实例的 `mods` 文件夹。
3. 本文件已包含 TaCZ 核心，内部模组 ID 为 `tacz`。不要同时启用原版 TaCZ 或其他包含 TaCZ 核心的替代版本。
4. 启动游戏，在模组列表确认 **TaCZ-JYVR 0.1.0**，进入 Vivecraft VR 模式。

### 已知兼容限制

- `tacz_recycling-1.20.1-1.6_release.jar` 包含另一套 TaCZ 核心，不能与本版本并用。
- `TaczBulletImprovement-Forge-1.20.1-1.0.4.jar`（`tacz_instanthit`）要求 `tacz >= 1.0`，与本版注册版本 `0.1.0` 不匹配，当前需禁用。
- 不保证所有 TaCZ 附属模组、枪包和光影组合均兼容。

## 使用

进入世界后，可通过聊天指令检查交互状态：

```text
/jyvr status
/jyvr debug on
/jyvr debug off
```

F8 切换交互判定箱。弹壳设置示例：

```text
/jyvr casings on
/jyvr casings lifetime 5
```

完整参数、权限和配置项见 [指令与使用说明](docs/commands.md)。

自定义枪械校准文件为 `config/tacz-jyvr-profiles.json`，可参考 [配置示例](docs/tacz-jyvr-profiles.example.json)。

## 从源码构建

需要 **完整 JDK 17** 和可下载 Gradle、Forge、Maven 依赖的网络环境。确认 `JAVA_HOME` 指向 JDK 17 根目录。

Windows：双击根目录的 `build-vr.bat`，或在终端运行：

```bat
gradlew.bat build jarJar -PvrReleaseBuild --no-daemon --max-workers=1
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew build jarJar -PvrReleaseBuild --no-daemon --max-workers=1
```

可安装的完整产物为 `build/libs/TaCZ-JYVR-0.1.0.jar`。保留仓库中的 `libs` 和 `gradle/wrapper` 文件；它们参与构建。

## 问题反馈

请在本仓库 Issues 提供 Minecraft、Forge、Vivecraft 版本，枪械及枪包名称、复现步骤，以及同次启动的 `logs/latest.log`。涉及倍镜问题时，可以运行 `/jyvr scope capture`，关闭聊天并在 30 秒内开镜，附上诊断截图。

提交日志前请自行检查其中的个人信息。`verification` 中的测试不替代头显、手柄和游戏内联调。

## 来源与许可

本项目基于 [Timeless and Classics Guns Zero（TaCZ）](https://github.com/MCModderAnchor/TACZ) 修改，VR 运行环境由 [Vivecraft](https://github.com/Vivecraft/VivecraftMod) 提供。本仓库不是上述项目的官方发行版。

保留 TaCZ 原项目作者署名：

- 程序：junyun1337
- 美术：junyun1337

原项目许可声明：

- 代码：[GNU GPL 3.0](LICENSE)
- 原项目资源：[CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/)

第三方依赖保留各自许可证及署名。
