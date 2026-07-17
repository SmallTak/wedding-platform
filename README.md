# Wedding Platform

婚礼作品展示官网与云相册内容平台。

完整需求、页面结构、权限矩阵、业务流程、数据库设计和技术架构见
[婚礼作品展示与云相册平台方案](docs/婚礼作品展示与云相册平台方案.md)。

跨电脑继续开发前，先阅读：

- [开发交接](docs/开发交接.md)：当前进度、线上状态和下一步。
- [变更日志](CHANGELOG.md)：按时间记录实现、验证和部署结果。
- [部署说明](deploy/README.md)：iot 服务器、Nginx、证书和发布流程。

## 工程结构

```text
.
├── wedding-web/       # 公开官网与客户中心
├── wedding-console/   # 管理员与创作者工作台
├── wedding-server/    # Spring Boot API
├── deploy/            # 部署配置
└── docs/              # 需求与技术文档
```

## 本地开发

### 后端

```bash
cd wedding-server
./gradlew bootRun
```

本地默认使用文件型 H2 数据库，生产环境通过 `prod` profile 连接 MySQL。

本地管理员账号：

- 手机号：`13800000000`
- 密码：`Admin@123456`

该账号仅由 `local` profile 初始化，生产环境需要通过环境变量显式开启首个管理员初始化。
新数据库首次初始化的管理员必须先修改临时密码。

### 官网

```bash
cd wedding-web
npm install
npm run dev
```

### 工作台

```bash
cd wedding-console
npm install
npm run dev
```

默认端口：

- API：`8080`
- 官网：`5173`
- 工作台：`5174`

## 当前进度

- 已完成 Vue 官网与管理工作台的首版可运行页面。
- 已完成 Spring Boot 4.1 基础工程、RBAC 基础表和 Flyway 初始化。
- 已完成手机号密码登录、JWT 鉴权、前端路由守卫和资源权限校验。
- 已完成管理员开通、启停和重置创作者账号。
- 已完成创作者首次改密、头像上传和资料完善流程。
- 已配置本地 H2 与生产 MySQL profile、图片上传大小和服务器存储目录。
- 已部署到 iot，官网、工作台、API、HTTPS 和生产管理员登录均已验证。
- 下一阶段按方案文档实现婚礼项目、作品集、作品图片上传和多人协作。
