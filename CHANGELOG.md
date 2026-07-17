# 变更日志

本文件按时间倒序记录项目实现、验证与部署结果。每次代码、数据库、配置或线上状态变化时，
必须与 `docs/开发交接.md` 在同一提交更新。禁止记录数据库密码、JWT 密钥、证书私钥或长期账号密码。

## 2026-07-17

### 阶段二工作台与本机生产发布

- 工作台新增婚礼项目列表、筛选、创建、编辑和管理员参与创作者分配。
- 工作台新增分类标签创建、编辑、启停和排序管理。
- 工作台新增作品集筛选、创建、编辑、项目关联、分类标签和管理员共同创作者分配。
- 工作台新增作品图片批量上传、预览、完整排序、封面设置和逻辑删除。
- 新增阶段二统一 API 封装、状态与错误提示工具，并按权限资源接入路由和导航。
- 工作台 Vite API 代理支持通过 `VITE_API_PROXY_TARGET` 覆盖目标地址。
- 新增 `deploy/scripts/deploy-local.sh`，支持在生产主机直接执行测试、构建、备份、
  应用与 Nginx 发布、失败回滚和正式域名验收。
- 更新 `deploy/README.md`、阶段二接口文档和开发交接。
- 验证：`bash -n deploy/scripts/deploy-local.sh` 和 `git diff --check` 通过。
- 验证：`./deploy/scripts/deploy-local.sh` 内执行后端 `clean test`，13 个测试通过。
- 验证：官网、工作台和 Spring Boot `bootJar` 生产构建通过；工作台仍有主 chunk
  超过 500 kB 的警告。
- 部署：Flyway `V4`、`V5`、阶段二后端、工作台页面和 Nginx 媒体路由已部署到
  `photo.shop-hz.top`，生产 schema 当前为 `V5`。
- 部署：systemd、Nginx 和构建产物比对通过；官网、工作台、工作台深层路由和 API
  返回成功状态，原图测试路径返回 `404`。
- 回滚点：`/home/apps/wedding-platform/backups/20260717-214359`。

### 阶段二作品图片处理

- 新增 Flyway `V5`，建立媒体资源、作品图片及作品集封面外键。
- 实现 JPEG/PNG 真实格式校验、尺寸与 SHA-256 提取、UUID 分层存储。
- 实现统一水印预览图和缩略图生成，原图路径不通过接口或 Nginx 公开。
- 实现作品图片批量上传、完整排序、封面设置、逻辑删除和作品集版本递增。
- 实现图片操作的权限、参与关系、发布锁、乐观锁和操作日志校验。
- 增加上传事务回滚后的文件清理与对应集成测试。
- 新增 `docs/阶段二图片接口.md`，并补充 Nginx 预览图和缩略图静态路由。
- 改进全量构建脚本的 Node.js/Docker 兼容路径，并修复 Java 17 版本识别。
- 验证：`cd wedding-server && JAVA_HOME=/opt/java/jdk17 PATH=/opt/java/jdk17/bin:$PATH ./gradlew clean test`，13 个测试通过。
- 验证：`./deploy/scripts/build-all.sh`，官网、工作台和 Spring Boot `bootJar` 构建通过。
- 验证：使用临时主配置包含 `deploy/nginx/photo.shop-hz.top.conf` 执行 Nginx `-t`，语法检查通过。
- 部署：未执行，本次阶段二数据库、后端接口和媒体 Nginx 路由尚未上线。

### 阶段二分类与作品集协作

- 实现管理员分类和标签查询、创建、编辑、启停、排序及重复名称校验。
- 实现作品集表单启用分类标签选项接口。
- 实现独立或关联项目的作品集创建、编辑、详情和分页查询。
- 实现作品集共同创作者分配、创作者数据范围和关联项目参与关系校验。
- 实现作品集乐观锁、已发布锁定及分类、标签、作品集操作日志。
- 新增 3 个作品集流程集成测试与 `docs/阶段二作品集接口.md`。
- 验证：`JAVA_HOME=/opt/java/jdk17 PATH=/opt/java/jdk17/bin:$PATH ./gradlew test`，10 个测试通过。
- 验证：`JAVA_HOME=/opt/java/jdk17 PATH=/opt/java/jdk17/bin:$PATH ./gradlew bootJar`。
- 部署：未执行，本次仅完成本地开发与验证。

### 阶段二婚礼项目基础

- 新增 Flyway `V4`，建立婚礼项目、项目参与者、分类、标签、作品集及关联表。
- 新增统一业务实体字段和项目、分类、标签、作品集 JPA 基础模型。
- 实现婚礼项目创建、编辑、详情、分页查询和管理员参与者分配接口。
- 实现创作者“创建人或参与者”数据范围、已发布锁定、乐观锁版本校验和操作日志。
- 新增项目流程集成测试与 `docs/阶段二项目接口.md`。
- 验证：`JAVA_HOME=/opt/java/jdk17 PATH=/opt/java/jdk17/bin:$PATH ./gradlew test`，7 个测试通过。
- 验证：`JAVA_HOME=/opt/java/jdk17 PATH=/opt/java/jdk17/bin:$PATH ./gradlew bootJar`。
- 部署：未执行，本次仅完成本地开发与验证。

### 文档交接规范

- 新增 `docs/开发交接.md`，集中记录当前阶段、生产状态、安全约束、最近验证和下一步。
- 更新 `AGENTS.md`，要求后续每次变更同步维护交接文档和本日志。
- 校正 Java 版本、数据库用户、DNS 状态和证书路径等陈旧说明。
- 验证：人工核对当前代码、部署配置、Git 状态和线上已完成检查。

### 生产登录修复

- 将正式域名及其 `www` 别名加入 Spring Security CORS 白名单。
- 增加正式域名 Origin 登录集成测试。
- 创建首个生产管理员，并要求首次登录修改临时密码。
- 初始化完成后关闭管理员引导，并清空环境文件中的临时密码。
- 验证：`./gradlew test`、`bootJar`、iot 服务重启、正式 Origin 正确凭据返回 `200`、错误凭据返回 `401`。

### 域名与 HTTPS

- 配置 `photo.shop-hz.top` A 记录指向 `47.104.21.68`。
- 更新并部署重新签发的 DigiCert 证书，覆盖主域名和 `www` 别名。
- Nginx 配置检查与重载通过，旧证书已在服务器备份。
- 验证：官网与工作台返回 `200`，API 状态返回 `UP`，线上证书序列号与新证书一致。

### 首次生产部署

- 将官网、工作台和 Spring Boot 服务部署到 iot。
- 使用独立 `wedding_platform` schema 和 `wedding_app` 数据库用户，Flyway 迁移至 `V3`。
- 后端改为 Java 17 目标，适配 iot 的 JDK 17。
- 配置 systemd、Nginx、HTTPS 部署和回滚脚本。
- 验证：后端测试和 `bootJar`、两个 Vue 生产构建、生产 Flyway、systemd、Nginx 及 API 健康检查通过。

### 阶段一账号体系

- 实现手机号密码登录、JWT、RBAC、创作者账号管理和多职业角色。
- 实现首次改密、头像上传、资料完善、前后端访问保护和操作日志。
- 新增 Flyway `V1` 至 `V3` 及完整账号流程集成测试。
- 验证：`./gradlew test`、官网与工作台生产构建通过。
