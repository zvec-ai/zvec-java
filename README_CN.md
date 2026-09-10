# zvec-java

[English](README.md) | 简体中文

**zvec-java** 是 [Zvec](https://github.com/alibaba/zvec) 向量数据库 C API 的工业级 Java 语言绑定,底层基于 [JavaCPP](https://github.com/bytedeco/javacpp) 生成 JNI 绑定。JavaCPP 会从 `zvec/c_api.h` 自动生成 JNI 胶水代码,并将各平台原生库打包进 JAR、运行时自动解压加载,**无需任何手写 JNI 代码,也无需用户手动配置库路径**。

## 特性

- **JavaCPP + JNI**:由 JavaCPP 解析 `c_api.h` 自动生成低层绑定类 `ZvecNative` 与 JNI 胶水,兼顾性能与可维护性
- **跨平台开箱即用**:原生库(`libzvec_c_api` + `libjniZvecNative`)按 `平台-架构` 目录打进 JAR,运行时零配置自动加载
- **高层包装类**:在生成的 `ZvecNative` 之上提供类型安全、资源安全的 Java 对象
- **AutoCloseable 资源管理**:所有持有原生资源的对象均实现 `AutoCloseable`,支持 try-with-resources
- **丰富的索引支持**:HNSW、IVF、Flat、Invert(倒排)、Vamana、DiskANN、IVF-RaBitQ(zvec ≥ v0.7.0)及量化变体(FP16/INT8/INT4/RaBitQ)。平台可用性与 zvec 本体一致:DiskANN 需要 Linux x86_64/ARM64 或 macOS ARM64,IVF-RaBitQ 需要 Linux x86_64,其他平台原生层返回 `NotSupported`
- **文档迭代器**:支持对集合做快照遍历,可选择输出字段(`Collection.createIterator`,zvec ≥ v0.7.0)
- **Jieba 全文索引开箱即用**:JAR 内置 cppjieba 词表(`jieba.dict.utf8` + `hmm_model.utf8`,位于 `zvec/jieba_dict/`),`Zvec.initialize()` 时自动注册,`jieba` 分词器无需任何额外配置
- **多种数据类型**:支持 30 余种字段类型,包括各维度稀疏/稠密向量
- **Java 8+**:最低兼容 Java 8
- **120 个单元测试**:全部通过;关键 DML/DQL 采用强断言(topK 数量/score 排序/PK 命中、update 回读、delete 移除校验)。涉及平台受限索引的用例在 zvec 未编译该索引的平台上自动跳过

## 安装

发布产物已上传 **Maven Central**,坐标为 `org.zvec:zvec-java`。加上依赖就是全部准备工作:JAR 内已经带了各平台原生库和 cppjieba 词表,**不需要单独安装原生库,也不需要配置任何库路径**。

**Maven**

```xml
<dependency>
    <groupId>org.zvec</groupId>
    <artifactId>zvec-java</artifactId>
    <version>0.7.0</version>
</dependency>
```

**Gradle**

```groovy
implementation 'org.zvec:zvec-java:0.7.0'
```

`org.bytedeco:javacpp` 会作为传递依赖自动引入,无需自己声明。运行环境要求 Java 8 及以上。

对外 API 都在 `org.zvec.binding` 包下:`Zvec`、`Collection`、`Doc`、`Schema`、`IndexParams`、`VectorQuery` 等都在这里,而不是 `org.zvec`。

### 选择哪个产物

版本号跟随内置的 zvec 原生库版本:`0.7.0` 对应 zvec v0.7.0。

| 产物 | 内容 | 适用场景 |
|------|------|----------|
| *(不带 classifier)* | classes + jieba 词表 + **全部**受支持平台的原生库 | 想用一个依赖跑遍所有平台。最省事,体积最大。 |
| `macosx-arm64` | classes + jieba 词表 + macOS ARM64 原生库 | 部署平台确定,想要更小的体积。 |
| `linux-x86_64` | classes + jieba 词表 + Linux x86_64 原生库 | 同上,Linux x86_64。 |
| `windows-x86_64` | classes + jieba 词表 + Windows x86_64 原生库 | 同上,Windows x86_64。 |
| `nolib` | classes + jieba 词表,**不含**原生库 | 自己编译或分发 `zvec_c_api`,再让加载器指向它(见[原生库如何加载?](#原生库如何加载))。 |

指定单平台 classifier:

```xml
<dependency>
    <groupId>org.zvec</groupId>
    <artifactId>zvec-java</artifactId>
    <version>0.7.0</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

```groovy
implementation 'org.zvec:zvec-java:0.7.0:linux-x86_64'
```

受支持平台:macOS ARM64、Linux x86_64、Windows x86_64。各类索引的平台可用性仍与 zvec 本身一致(见[特性](#特性))。

接下来直接看[代码示例](#代码示例)即可,唯一需要的初始化调用是 `Zvec.initialize(null)`。

## 快速开始

以下内容是从源码构建绑定的流程,适用于开发调试,或者需要为发布产物未覆盖的平台/架构自行编译原生库的场景。如果只是想**使用** zvec-java,按[安装](#安装)加上 Maven Central 依赖就够了,可以直接跳到[代码示例](#代码示例)。

### 前置条件

| 工具 | 最低版本 | 用途 |
|------|---------|------|
| JDK  | 8       | 编译与运行 |
| Maven | 3.6    | 构建 |
| C++ 编译器 (clang / gcc / MSVC) | 支持 C++17 | JavaCPP 编译 JNI 胶水 |
| CMake + Ninja | 3.30 / 1.11 | 编译 Zvec C 库 |

### 获取源码(含子模块)

Zvec 核心以 **git submodule** 形式引入到 `./zvec`:

```bash
git clone https://github.com/zvec-ai/zvec-java.git
cd zvec-java
git submodule update --init --recursive
```

### 编译 Zvec C 库

```bash
cd zvec
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_C_BINDINGS=ON -G Ninja
cmake --build . --target zvec_c_api -j
# 产物位于 zvec/build/lib/
cd ../..
```

### 构建 Java 绑定

```bash
# 默认从子模块 ./zvec 读取头文件与库(zvec.home=${project.basedir}/zvec)
mvn package

# 若复用一个已存在的 Zvec 检出(例如同级目录),用 -Dzvec.home 覆盖:
mvn package -Dzvec.home=/path/to/zvec
```

构建期涉及的关键属性:

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `zvec.home` | `${project.basedir}/zvec` | Zvec 核心根目录(子模块) |
| `zvec.include.path` | `${zvec.home}/src/include` | 头文件目录(JavaCPP parse) |
| `zvec.lib.path` | `${zvec.home}/build/lib` | 链接库目录(JavaCPP link) |

### 运行测试

```bash
mvn test
# 或指定 Zvec 位置
mvn test -Dzvec.home=/path/to/zvec
```

### 运行示例

打包生成的 fat JAR **已内置当前平台的原生库**,运行时无需任何库路径配置:

```bash
mvn package -DskipTests
java -jar target/zvec-java-0.7.0-with-dependencies.jar
```

## 项目结构

```
zvec-java/
├── pom.xml                                          # Maven 构建(JavaCPP 插件两段式:parse + build)
├── zvec/                                            # git submodule:Zvec 核心
└── src/
    ├── main/java/org/zvec/binding/
    │   ├── presets/ZvecConfig.java                  # JavaCPP InfoMapper:指导解析 c_api.h
    │   ├── ZvecNative.java                          # 【自动生成】低层 JNI 绑定(勿手改,已在 .gitignore)
    │   ├── NativeSupport.java                        # String <-> const char* 等桥接工具
    │   ├── Zvec.java                                # 顶层入口:初始化、版本、Collection 工厂
    │   ├── Collection.java                          # Collection 操作(增删改查、搜索)
    │   ├── CollectionOptions.java / CollectionSchema.java / CollectionStats.java
    │   ├── FieldSchema.java                         # 字段 Schema 定义
    │   ├── Doc.java                                 # 文档 CRUD(读写各类型字段)
    │   ├── IndexParams.java                         # 索引参数(HNSW/IVF/Flat/Invert)
    │   ├── VectorQuery.java / GroupByVectorQuery.java
    │   ├── ConfigData.java / LogConfig.java
    │   ├── ZvecException.java
    │   └── DataType / IndexType / MetricType / QuantizeType / LogLevel / DocOperator / ErrorCode (枚举)
    └── test/java/org/zvec/binding/
        ├── ZvecTest.java                            # 基础 API 测试
        ├── TestSupport.java                         # 测试基类(守护式 init + 索引集合/向量助手)
        ├── DocCoverageTest.java                     # Doc 元数据/UTF-8/异常 强断言
        ├── SchemaIndexConfigCoverageTest.java       # Schema/IndexParams(out 参数)/Config/异常
        ├── CollectionQueryCoverageTest.java         # DML/DQL 强断言(query/update/delete/filter)
        ├── ApiCoverageTest.java                     # 更广的 API 面:枚举码、DiskANN/IVF-RaBitQ/FTS 参数、multi-query、迭代器、I/O 后端、jieba 词表
        └── SearchIntegrationTest.java               # 端到端检索:纯 FTS、向量 + FTS 混合、multi-query 多路子查询
```

## 代码示例

### 初始化与关闭

```java
// 使用默认配置初始化
Zvec.initialize(null);

// 自定义配置
try (ConfigData config = new ConfigData()) {
    config.setQueryThreadCount(4);
    config.setMemoryLimit(512 * 1024 * 1024L); // 512MB
    config.setConsoleLog(LogLevel.INFO);
    Zvec.initialize(config);
}

// 关闭(进程退出前调用)
Zvec.shutdown();
```

### 定义 Schema 并创建 Collection

```java
CollectionSchema schema = new CollectionSchema("my_collection");

// FP32 向量字段
try (FieldSchema vecField = new FieldSchema("embedding", DataType.VECTOR_FP32, false, 128)) {
    try (IndexParams hnsw = IndexParams.createHNSW(MetricType.L2, 32, 200)) {
        vecField.setIndexParams(hnsw);
    }
    schema.addField(vecField);
}

// 元数据字段
try (FieldSchema titleField = new FieldSchema("title", DataType.STRING, true, 0)) {
    schema.addField(titleField);
}

// 创建并打开 Collection
Collection coll = Zvec.createAndOpen("/tmp/my_db", schema, null);
schema.close();
```

### 插入与查询

```java
// 插入文档
List<Doc> docs = new ArrayList<>();
Doc doc = new Doc();
doc.setPK("doc_001");
doc.addStringField("title", "Hello Zvec");
doc.addVectorFP32Field("embedding", new float[128]); // 示意:全零向量
docs.add(doc);
coll.insert(docs);
Doc.freeDocs(docs);
coll.flush();

// 向量查询
try (VectorQuery query = new VectorQuery()) {
    query.setTopK(10);
    query.setFieldName("embedding");
    query.setQueryVector(new float[128]); // 查询向量

    List<Doc> results = coll.query(query);
    for (Doc d : results) {
        System.out.printf("id=%s, score=%.4f%n", d.getPK(), d.getScore());
    }
    Doc.freeDocs(results); // 结果由原生内存支持,用后必须释放
}
coll.close();
```

### 索引类型

```java
IndexParams hnsw   = IndexParams.createHNSW(MetricType.L2, 32, 200);
IndexParams hnswQ  = IndexParams.createHNSWQuantized(MetricType.IP, 32, 200, QuantizeType.FP16);
IndexParams ivf    = IndexParams.createIVF(MetricType.COSINE, 256, 100, false);
IndexParams flat   = IndexParams.createFlat(MetricType.L2);
IndexParams invert = IndexParams.createInvert(true, false); // 倒排,用于文本/标签
```

## 依赖

| 依赖 | 版本 | 用途 | 许可证 |
|------|------|------|--------|
| `org.bytedeco:javacpp` | 1.5.11 | JNI 代码生成 + 跨平台原生库加载 | Apache-2.0 **或** GPL-2.0-or-later **或** GPL-2.0-with-classpath-exception；本项目按 Apache-2.0 使用 |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | 单元测试(仅 test scope)| EPL-2.0 |

## 架构

```
  Java 应用代码
       │
       ▼
  高层 API(Zvec / Collection / Doc / VectorQuery …)   类型安全 + AutoCloseable
       │
       ▼
  ZvecNative(JavaCPP 自动生成的 JNI 绑定)
       │  JavaCPP 生成的 JNI 胶水(libjniZvecNative)
       ▼
  libzvec_c_api.(so|dylib|dll)
       │
       ▼
  Zvec C++ 核心引擎
```

构建流程:JavaCPP `Parser` 解析 `c_api.h`(由 `presets/ZvecConfig` 指导)生成 `ZvecNative.java` → 编译 → JavaCPP `Generator/Compiler` 生成并编译 JNI 胶水为 `libjniZvecNative`,链接 `zvec_c_api` → 两者一并打进 JAR 的 `平台-架构` 目录。

## 常见问题

### 原生库如何加载?

原生库(`zvec_c_api` + JavaCPP JNI 胶水 `jnizvec`)由 `NativeLoader` 按**三级优先级**(从高到低)解析:

1. **第一级 · 显式路径**:设置 `-Dzvec.native.path=/dir` 或环境变量 `ZVEC_NATIVE_PATH`,即优先从该目录解析 `zvec_c_api`(内部通过 JavaCPP 的 `pathsFirst` + `platform.preloadpath` 实现)。适用于本地开发或使用自建/自定义原生库。
2. **第二级 · classpath / fat JAR**:默认行为——从 JAR 内 `平台-架构` 资源目录解压到临时目录并加载,**无需任何库路径配置**。
3. **第三级 · 系统库路径**:兜底回退到 `java.library.path` / `LD_LIBRARY_PATH` / `DYLD_LIBRARY_PATH` / `PATH`。

加载时按第一级 → 第二级 → 第三级依次尝试,三级均失败时会抛出**列明上述来源的可读 `UnsatisfiedLinkError`**,便于定位。

```bash
# 第一级:指向本地已构建的原生库目录
java -Dzvec.native.path=/path/to/zvec/build/lib -jar app.jar
# 或用环境变量
ZVEC_NATIVE_PATH=/path/to/zvec/build/lib java -jar app.jar
```

本地 `mvn package` 产出的 fat JAR 只含**构建时所在平台**的原生库;而 CI 的 **Publish JAR** 工作流会在各平台分别构建、聚合出**含全部平台原生库的多平台 fat JAR**(见 `.github/workflows/publish-jar.yml`)。发布到 Maven Central 的 `org.zvec:zvec-java` 就是这个多平台 JAR,同时还会发布[安装](#安装)一节中列出的单平台 classifier JAR 与 `nolib` JAR。

### `Collection.fetch()` 返回空 / InvalidArgument

`fetch()` 需要目标字段建立了 **forward index**(正排索引)。确保 schema 中为需要 fetch 的字段配置了正排索引,或改用 `query()`。

### 内存管理

- 所有实现了 `AutoCloseable` 的对象(`Collection`、`Doc`、`IndexParams`、`VectorQuery` 等)都应在 try-with-resources 块中使用,或在 finally 中显式 `close()`。
- `Collection.query()` / `Collection.fetch()` 返回的 `List<Doc>` 由原生内存支持,使用完毕后必须调用 `Doc.freeDocs(list)` 释放。

### `Doc.validate(...)`

Zvec C API 未提供文档级校验函数(`zvec_doc_validate` 不存在),该方法会抛出 `UnsupportedOperationException`;请改用 `CollectionSchema.validate()` / `FieldSchema.validate()`。

## 许可证

本项目采用 **Apache License 2.0**,与 Zvec 主项目保持一致,详见 [zvec/LICENSE](zvec/LICENSE)。

### 第三方许可证说明

- **JavaCPP** (`org.bytedeco:javacpp:1.5.11`) 采用三重许可证:
  `Apache-2.0 或 GPL-2.0-or-later 或 GPL-2.0-with-classpath-exception`。
  本项目按 **Apache-2.0** 条款使用 JavaCPP。
- **JUnit 5** 仅在 `test` scope 中使用,许可证为 EPL-2.0,不会被打入发布的 JAR。
- 原生库 `zvec_c_api`(由 `zvec` 子模块构建)可能包含 **RocksDB** 等第三方代码。
  RocksDB 的部分组件(例如源自 PerconaFT 的 `utilities/transactions/lock/range/range_tree/`)
  采用 GPL/AGPL 类许可证。二进制分发方应确认 `zvec` 核心的构建方式与自身期望的许可证条款兼容。
