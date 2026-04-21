此次合并主要涉及前端架构优化和后端依赖更新，包括将侧边栏组件化、存储方式从localStorage改为sessionStorage、添加代码质量工具和构建优化配置。后端更新了数据库依赖版本并改进了数据库操作方式，同时添加了代码规范工具。
| 文件 | 变更 |
|------|---------|
| backend/package.json | - 更新better-sqlite3从9.4.3到11.0.0<br>- 添加eslint、eslint-config-prettier、prettier作为开发依赖 |
| backend/services/authService.js | - 将数据库操作从直接使用db.prepare改为使用get、run、query函数<br>- 统一了数据库查询和更新操作的调用方式 |
| frontend/package.json | - 添加@vueuse/core依赖<br>- 添加@babel/eslint-parser、@vue/eslint-config-prettier、eslint、prettier作为开发依赖 |
| frontend/src/App.vue | - 将侧边栏逻辑拆分为单独的Sidebar组件<br>- 移除了暗色模式相关代码<br>- 简化了组件结构和样式 |
| frontend/src/api/index.js | - 将localStorage改为sessionStorage存储token和租户信息<br>- 优化了401错误处理逻辑 |
| frontend/src/stores/auth.js | - 将localStorage改为sessionStorage存储用户信息<br>- 添加onMounted钩子<br>- 优化了logout方法，清除所有相关存储 |
| frontend/vite.config.js | - 添加构建配置，包括代码分割、gzip压缩<br>- 配置资源内联和最小化选项 |
| frontend/.eslintrc.js | - 新增ESLint配置文件 |
| frontend/.prettierrc | - 新增Prettier配置文件 |
| frontend/src/components/Sidebar.vue | - 新增侧边栏组件，包含导航菜单、用户信息等功能 |
| frontend/src/components/DarkModeSwitch.vue | - 新增暗色模式切换组件 |
| frontend/src/components/LangSwitch.vue | - 新增语言切换组件 |
| frontend/src/directives/lazyLoad.js | - 新增图片懒加载指令 |