# AGENTS.md

## 项目定位

本仓库是婚礼作品展示官网与云相册内容平台，采用前后端分离的单体架构。

## 技术栈

- `wedding-web`：Vue 3、Vite、Vue Router、Pinia。
- `wedding-console`：Vue 3、Vite、Vue Router、Pinia、Element Plus。
- `wedding-server`：Java 17、Spring Boot、Gradle、Spring Data JPA、Spring Security、Flyway。
- 生产数据库：MySQL。
- 图片：服务器本地磁盘；Nginx 公开原图、预览图与缩略图，官网业务图片使用原图。

## 工程约束

- 官网、工作台和后端可独立构建与部署。
- 后端按业务模块组织，Controller 不堆业务流程。
- 权限采用用户、系统角色、权限资源三层模型。
- 职业角色与系统权限角色分离。
- 所有后台资源必须同时具备前端入口控制和后端方法级权限校验。
- 创作者操作必须额外校验项目或作品集参与关系。
- 项目、作品、审核、发布和下架操作保留审计日志。
- 数据库变更统一通过 `wedding-server/src/main/resources/db/migration` 管理。
- 业务删除默认逻辑删除，图片物理文件长期保留。
- 不提交 `node_modules`、`dist`、Gradle 构建目录和本地数据目录。

## 文档同步要求

- 开始工作前先阅读 `docs/开发交接.md` 和 `CHANGELOG.md`，再检查 Git 状态与最近提交。
- 每次代码、数据库、配置、部署或线上状态发生变化时，必须在同一提交更新文档。
- `docs/开发交接.md` 维护当前真实状态：已完成、正在进行、下一步、线上状态和已知问题。
- `CHANGELOG.md` 按时间倒序追加变更，至少记录变更内容、验证命令和部署结果。
- 新接口同步更新对应阶段接口文档；数据库变更同步记录 Flyway 版本。
- 只记录实际执行过的验证，不将计划中的检查写成已通过。
- 文档和提交中禁止记录数据库密码、JWT 密钥、证书私钥和长期账号密码。
- 任务完成前检查文档是否仍与代码和线上环境一致；未同步文档视为未完成。

## 最低验证

- `wedding-web`：`npm run build`
- `wedding-console`：`npm run build`
- `wedding-server`：`./gradlew test`
