# Wedding Platform

婚礼作品展示官网与云相册内容平台。

完整需求、页面结构、权限矩阵、业务流程、数据库设计和技术架构见
[婚礼作品展示与云相册平台方案](docs/婚礼作品展示与云相册平台方案.md)。

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
- 已配置本地 H2 与生产 MySQL profile、图片上传大小和服务器存储目录。
- 下一阶段按方案文档实现账号、项目、作品集、图片上传、审核发布和客户反馈模块。
