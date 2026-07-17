# Wedding Platform

婚礼作品展示官网与云相册内容平台。

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

