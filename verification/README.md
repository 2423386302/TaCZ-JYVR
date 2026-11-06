# 验证代码

此目录保存交互状态、弹药流转、碰撞、枪械模型、动画和倍镜渲染相关的独立回归检查。

- `*Regression.java`：相应模块的回归检查。
- `CollisionPerformance.java`：合成碰撞数据的性能检查。
- `export_mesh_fixtures.py` 与 `mesh-fixtures.txt`：模型测试数据导出及数据文件。
- `ScopeGLContext.c`：OpenGL 检查所需的本地上下文支持。
- `ParseJava.java`：Java 源码解析检查工具。

这些检查未统一接入 Gradle 的 `test` 任务。部分检查需要额外的 Java classpath、模型资源或 OpenGL 本地库；不能将普通 `gradlew build` 成功等同于本目录所有检查均已执行。

合成几何、状态机及离屏 OpenGL 检查不能替代游戏内头显跟踪、双眼显示、手柄操作、第三方光影和多人联调。运行输出不提交到仓库。
