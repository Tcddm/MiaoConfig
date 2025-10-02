# MiaoConfig 🐾

**Java新一代配置管理API，一切从简**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/java-8%2B-orange.svg)](https://java.com)
[![Java](https://img.shields.io/badge/java-21%2B-orange.svg)](https://java.com)
## ✨ 特性

- 🚀 **注解驱动**：使用 `@MiaoConfig` 和 `@MiaoValue` 轻松定义配置
- 🛡️ **类型安全**：编译时类型检查，运行时自动转换（支持基本类型、枚举、集合、嵌套对象等）
- 💾 **智能保存**：无修改不保存，优化IO性能
- 🔧 **弱引用管理**：自动清理不再使用的配置实例，避免长期运行的应用（如服务端程序）因配置实例累积导致的内存泄漏
- 🧩 **多格式支持**: 默认支持JSON格式，通过MiaoConfigParser接口可轻松扩展Properties、YAML等其他格式（仅需实现解析和序列化方法）
- 🔗 **嵌套配置**: 路径导航支持通过 "." 分隔符表示嵌套配置（如 "a.b.c"），轻松处理复杂结构的配置文件

## 🚀 基本使用示例
### 1.创建配置类
```java
import com.tcddm.miaoconfig.annotation.MiaoConfig;
import com.tcddm.miaoconfig.annotation.MiaoValue;

@MiaoConfig(
        configName = "serverConfig"// 关联的配置文件名称（对应addConfigFile时的name），默认为config
        , path = "server"// 配置在文件中的根路径（嵌套配置的顶层节点）
)
public class ServerConfig {
    @MiaoValue// 未指定path时，默认使用字段名作为路径（结合类的path，最终为"server.host"）
    private String host="localhost";
    
    @MiaoValue// 未指定path时，默认使用字段名作为路径（结合类的path，最终为"server.port"）
    private int port=8080;
    
    @MiaoValue(path = "ssl.enabled")//对应server.ssl.enabled
    private boolean sslEnabled=false;

    // getters & setters...
    @Override
    public String toString() {
        return "ServerConfig{host='" + host + "', port=" + port + ", sslEnabled=" + sslEnabled + "}";
    }
}
```
### 2.初始化配置
```java
import com.tcddm.miaoconfig.MiaoConfigFactory;

public class Main {
    public static void main(String[] args) {
        // 添加配置文件（支持文件路径、目录、File对象等）
        MiaoConfigFactory.getConfigFileManager()
                .addConfigFile("config/server.json");  // 加载配置文件

        // 创建并加载配置实例
        ServerConfig config = new ServerConfig();
        MiaoConfigFactory.getConfigClazzManager().load(config);

        // 使用配置
        System.out.println("当前配置: " + config);
        // 输出: 当前配置: ServerConfig{host='localhost', port=8080, sslEnabled=false}
    }
}
```
### 3.修改并保存配置
```java
// 修改配置
config.setPort(8081);
config.setSslEnabled(true);

// 保存配置（仅当有修改时才写入文件）
MiaoConfigFactory.getConfigClazzManager().saveConfig(config);
// 批量保存所有已加载的配置实例（仅当有修改时才写入文件）
MiaoConfigFactory.getConfigClazzManager().saveAllConfig();
// 保存配置（保存到内存）
MiaoConfigFactory.getConfigClazzManager().saveConfigToMemory(config);
// 批量保存所有已加载的配置实例（保存到内存）
MiaoConfigFactory.getConfigClazzManager().saveAllConfigToMemory();
```
此时 config/server.json 会被更新为：
```json
{
  "server": {
    "host": "localhost",
    "port": 8081,
    "ssl": {
      "enabled": true
    }
  }
}
```
## 📚 高级特性
### 多配置文件管理
支持同时管理多个配置文件，通过 configName 区分，没有则为去掉后缀的文件名：
```java
// 添加多个配置文件
MiaoConfigFactory.getConfigFileManager()
    .addConfigFile("app", "config/app.json")  // 命名为"app"
    .addConfigFile("config/database.json");  // 命名为"database"
```
### 自动扫描配置目录
批量加载目录下所有配置文件（支持递归子目录），但是命名统一为去掉后缀的文件名：
```java
// 加载config目录下所有配置文件
MiaoConfigFactory.getConfigFileManager()
    .addConfigFilePath("config/");
```
### 一次性字段（不持久化）
标记为 disposable 的字段不会被保存到文件，适合临时配置：
```java
@MiaoValue(disposable = MiaoIsEnable.ENABLE)
private String tempToken;  // 修改后不会被保存
```
### 父类字段继承
配置类继承父类时，父类中标记 @MiaoValue 的字段也会被自动处理：
```java
public class BaseConfig {
    @MiaoValue(path = "version")
    private String version;
}

@MiaoConfig(configName = "app")
public class AppConfig extends BaseConfig {
    @MiaoValue(path = "name")
    private String appName;
}
// AppConfig会同时加载version和appName字段
```
### 拓展支持的类型
```java
  // 扩展YAML格式示例
  public class YamlConfigParser implements MiaoConfigParser {
  private final Yaml yaml = new Yaml(); // 假设使用SnakeYAML库

  @Override
  public Map<String, Object> parse(String content) {
    return yaml.load(content); // 解析YAML为Map
  }

  @Override
  public String serialize(Map<String, Object> data) {
    return yaml.dump(data); // 序列化Map为YAML
  }

  @Override
  public String[] supportedExtensions() {
    return new String[]{".yaml", ".yml"};
  }
  }

  // 注册解析器（必须在添加配置文件之前执行，否则无法识别对应格式文件）
  MiaoConfigFactory.registerParser(new YamlConfigParser());

  // 之后再添加YAML文件
  MiaoConfigFactory.getConfigFileManager().addConfigFile("config/app.yaml"); // 此时会使用YamlConfigParser解析
  ```
## ❓ 常见问题
### Q: 配置文件不存在会报错吗？
A: 不会，MiaoConfig 会自动创建不存在的配置文件和父目录。
### Q: 支持哪些配置格式？
A: 默认支持 JSON，可通过实现 MiaoConfigParser 接口扩展（如 Properties、YAML）。
### Q: 如何处理类型转换失败？
A: 转换失败时会使用字段默认值，并打印警告日志，不影响程序运行。
### Q: 弱引用管理会导致配置丢失吗？
A: 不会，配置数据会持久化到文件，实例被回收后可重新从文件加载。
## 📄 许可证
本项目基于 MIT 许可证 开源，详情参见许可证文件。