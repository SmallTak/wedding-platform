# Deploy

第一阶段部署采用 Nginx、Spring Boot 和 MySQL，图片文件保存在服务器本地磁盘。

一个域名即可承载全部服务：

- `/`：公开官网。
- `/console/`：管理员与创作者工作台。
- `/api/`：Spring Boot API。

管理端生产构建命令：

```bash
VITE_BASE_PATH=/console/ npm run build
```

生产环境启动后端前至少需要设置：

```text
SPRING_PROFILES_ACTIVE=prod
DB_HOST=127.0.0.1
DB_NAME=wedding_platform
DB_USERNAME=wedding
DB_PASSWORD=replace-me
JWT_SECRET=replace-with-at-least-32-random-bytes
STORAGE_ROOT=/srv/wedding/storage
```

首次部署可以临时设置 `BOOTSTRAP_ADMIN_ENABLED=true`、`BOOTSTRAP_ADMIN_MOBILE` 和
`BOOTSTRAP_ADMIN_PASSWORD` 创建首个管理员，创建成功后关闭该开关。

正式部署配置将在业务接口和图片处理链路完成后补充，避免在端口、域名、存储路径和
备份策略尚未确定前固化环境参数。
