# Maven 构建命令选择指南（Spring Boot / Spring Cloud 项目）

## 适用场景

- 项目类型：Spring Boot / Spring Cloud 微服务项目
- 模块示例：`crm-admin`（可独立部署的应用模块，通常打包为可执行 jar）
- 构建工具：Maven

## 核心结论

- **对于最终部署的应用模块**（如 `crm-admin`、`gateway`、`order-service`）：  
  **推荐使用 `mvn clean package`**
- `install` 仅在**需要将模块安装到本地仓库供其他模块依赖**时才使用
- `compile` 是冗余的，因为 `package` 和 `install` 的生命周期已包含编译阶段

---

## 不同场景下的最优命令

| 使用场景 | 推荐命令 | 说明 |
|---------|----------|------|
| 本地快速打包验证 | `mvn clean package` | 默认包含编译、测试、打包，最常用 |
| 本地开发 + 运行调试 | `mvn clean package`<br>然后 `java -jar target/*.jar` | 无需 install，直接运行 jar |
| 跳过测试（加速打包） | `mvn clean package -DskipTests` | 只编译不执行测试，适合频繁打包 |
| 完全跳过测试代码编译 | `mvn clean package -Dmaven.test.skip=true` | 连测试代码都不编译，速度最快 |
| 多模块项目（只改当前模块） | `mvn clean package -pl crm-admin -am` | `-pl` 指定模块，`-am` 同时构建其依赖模块 |
| 多模块项目 + 并行构建 | `mvn clean package -T 1C -DskipTests` | 每个 CPU 核一个线程，提升构建速度 |
| 需要被其他本地项目依赖 | `mvn clean install` | 将 jar 安装到本地仓库（如供其它服务本地联调） |
| CI/CD 流水线（最终部署） | `mvn clean package` 或 `mvn clean deploy` | `deploy` 会上传到远程仓库（如 Nexus） |
| 仅重新编译，不打包 | `mvn clean compile` | 极少单独使用，通常不需要 |

---

## Spring Boot 特别说明

1. **打包产物**  
   Spring Boot 项目使用 `spring-boot-maven-plugin`，`package` 阶段会生成可执行 jar（包含所有依赖及内嵌容器）。  
   命令 `mvn clean package` 即可得到 `xxx.jar`，可直接通过 `java -jar` 运行。

2. **常见优化**
    - 结合 `-DskipTests` 跳过单元测试，加速开发阶段构建
    - 结合 `-T 1C` 并行构建，改善多模块项目速度
    - 使用 `-Dspring.profiles.active` 参数指定启动配置，但这与打包命令无关，属于运行参数

3. **示例（典型开发命令）**
   ```bash
   mvn clean package -DskipTests -T 1C
