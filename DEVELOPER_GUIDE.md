# Vertigo Connectors — Developer Guide

> **Version:** 5.0.0-SNAPSHOT
> **Framework:** [vertigo-core](https://github.com/vertigo-io/vertigo-core) 5.x
> **Java:** 21+
> **License:** Apache 2.0

---

## Table of Contents

1. [Overview](#1-overview)
2. [Project Structure](#2-project-structure)
3. [Core Concepts](#3-core-concepts)
4. [Available Connectors](#4-available-connectors)
5. [How to Use a Connector](#5-how-to-use-a-connector)
6. [How to Create a New Connector](#6-how-to-create-a-new-connector)
7. [Configuration Reference](#7-configuration-reference)
8. [SSL / TLS Configuration](#8-ssl--tls-configuration)
9. [Testing](#9-testing)
10. [Proposed Improvements](#10-proposed-improvements)

---

## 1. Overview

`vertigo-connectors` is a collection of **21 thin adapter modules** that bridge the Vertigo IoC container to external services (Redis, MongoDB, Elasticsearch, LDAP, MQTT, …).

Each connector follows a single contract:
- implements `Connector<T>` where `T` is the native client type of the technology
- is declared in a `*Features` builder class for fluent configuration
- participates in the Vertigo lifecycle via the optional `Activeable` interface
- is injected like any other Vertigo component using `@Inject`

```
┌──────────────────────────────────────────────────────┐
│                   Your Application                   │
│                                                      │
│   @Inject  RedisConnector redis;                     │
│   UnifiedJedis client = redis.getClient();           │
└──────────────────────────┬───────────────────────────┘
                           │ Connector<UnifiedJedis>
┌──────────────────────────▼───────────────────────────┐
│              vertigo-redis-connector                  │
│                                                      │
│  RedisConnector  ──►  Jedis pool / cluster           │
└──────────────────────────────────────────────────────┘
```

---

## 2. Project Structure

```
vertigo-connectors/
├── pom.xml                              ← aggregator POM
├── vertigo-redis-connector/
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/vertigo/connectors/redis/
│       │   ├── RedisConnector.java      ← implementation
│       │   ├── RedisFeatures.java       ← declarative configuration
│       │   ├── VJedisPooled.java        ← pool-safe wrapper
│       │   ├── VJedisCluster.java
│       │   └── VJedisSentineled.java
│       └── test/java/io/vertigo/connectors/redis/
│           └── RedisConnectorTest.java
├── vertigo-mongodb-connector/
├── vertigo-httpclient-connector/
├── vertigo-elasticsearch-connector/
└── …                                    ← 21 modules total
```

Every module follows the same layout:

| Folder | Content |
|---|---|
| `src/main/java/io/vertigo/connectors/<name>/` | `*Connector.java`, `*Features.java`, helpers |
| `src/test/java/io/vertigo/connectors/<name>/` | `*ConnectorTest.java`, test data |

---

## 3. Core Concepts

### 3.1 `Connector<T>`

The base interface every connector implements:

```java
public interface Connector<T> extends CoreComponent {
    /** Returns the native client. */
    T getClient();

    /** Logical name — allows multiple connectors of the same type. */
    default String getName() { return "main"; }
}
```

### 3.2 `Activeable`

Connectors that manage a connection pool or long-lived resource also implement `Activeable`:

```java
public interface Activeable {
    void start();   // called by Vertigo after construction
    void stop();    // called by Vertigo before destruction
}
```

`start()` is typically a no-op; `stop()` closes the underlying connection pool.

### 3.3 `*Features` builder

Each module exposes a `Features` subclass. This is the only entry point for configuring connectors:

```java
public final class RedisFeatures extends Features<RedisFeatures> {

    public RedisFeatures() { super("vertigo-redis-connector"); }

    @Feature("jedis")
    public RedisFeatures withJedis(final Param... params) {
        getModuleConfigBuilder().addConnector(RedisConnector.class, params);
        return this;
    }

    @Override
    protected void buildFeatures() { /* mandatories */ }
}
```

### 3.4 `@ParamValue`

Constructor parameters are declared with `@ParamValue`. Optional parameters use `Optional<T>`:

```java
@Inject
public MyConnector(
        @ParamValue("name")     final Optional<String>  connectorNameOpt,   // optional
        @ParamValue("host")     final String            host,               // required
        @ParamValue("port")     final int               port,               // required
        @ParamValue("password") final Optional<String>  passwordOpt) {      // optional
    …
}
```

### 3.5 Multiple connector instances

Multiple instances of the same type can coexist, distinguished by `name`:

```java
NodeConfig.builder()
    .addModule(new RedisFeatures()
        .withJedis(Param.of("name", "cache"),   Param.of("host", "cache-host"), …)
        .withJedis(Param.of("name", "session"), Param.of("host", "session-host"), …)
        .build())
    .build();
```

Inject by name with a qualifier:

```java
@Inject @Named("cache")   private RedisConnector cacheRedis;
@Inject @Named("session") private RedisConnector sessionRedis;
```

---

## 4. Available Connectors

### Data Stores

| Module | Connector class | Client type | Notes |
|---|---|---|---|
| `vertigo-redis-connector` | `RedisConnector` | `UnifiedJedis` | Single / Sentinel / Cluster |
| `vertigo-redis-connector` | `RedisSingleConnector` | `UnifiedJedis` | **Deprecated** — use `RedisConnector` |
| `vertigo-mongodb-connector` | `MongoClientConnector` | `MongoClient` | Connection-string based |
| `vertigo-neo4j-connector` | `Neo4JConnector` | `Driver` | GPL embedded server optional |
| `vertigo-influxdb-connector` | `InfluxDbConnector` | `InfluxDBClient` | Token authentication |
| `vertigo-elasticsearch-connector` | `ElasticSearchConnector` | `RestHighLevelClient` | HTTP REST |

### Messaging

| Module | Connector class | Client type |
|---|---|---|
| `vertigo-mqtt-connector` | `MosquittoConnector` | `MqttClient` |

### HTTP / REST

| Module | Connector class | Client type | Notes |
|---|---|---|---|
| `vertigo-httpclient-connector` | `HttpClientConnector` | `HttpClient` | JDK 11+, factory pattern |

### Authentication / Security

| Module | Connector class | Notes |
|---|---|---|
| `vertigo-ldap-connector` | `LdapConnector` | LDAP directory |
| `vertigo-oidc-connector` | `OIDCDeploymentConnector` | OpenID Connect |
| `vertigo-saml2-connector` | `SAML2DeploymentConnector` | SAML 2.0 |
| `vertigo-keycloak-connector` | Servlet filter | Keycloak SSO |

### Cloud / Infrastructure

| Module | Connector class | Notes |
|---|---|---|
| `vertigo-azure-connector` | `AzureAdConnector` | Azure AD / Entra |
| `vertigo-openstack-connector` | `OpenStackConnector` | Object storage |
| `vertigo-s3-connector` | `S3Connector` | Minio client |

### Framework Bridges

| Module | Connector class | Notes |
|---|---|---|
| `vertigo-spring-connector` | configuration beans | Spring 7.x DI bridge |
| `vertigo-javalin-connector` | — | Javalin HTTP framework |

### Utilities

| Module | Notes |
|---|---|
| `vertigo-mail-connector` | SMTP / email |
| `vertigo-jsch-connector` | SSH (JSch) |
| `vertigo-ifttt-connector` | IFTTT webhooks |
| `vertigo-twitter-connector` | Twitter4j |

---

## 5. How to Use a Connector

### Step 1 — Add the Maven dependency

```xml
<dependency>
    <groupId>io.vertigo</groupId>
    <artifactId>vertigo-redis-connector</artifactId>
    <version>5.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2 — Register in NodeConfig

```java
NodeConfig.builder()
    .addModule(new RedisFeatures()
            .withJedis(
                    Param.of("host",     "localhost"),
                    Param.of("port",     "6379"),
                    Param.of("ssl",      "false"),
                    Param.of("database", "0"))
            .build())
    .build();
```

### Step 3 — Inject and use

```java
public class MyService implements Component {

    private final RedisConnector redisConnector;

    @Inject
    public MyService(final RedisConnector redisConnector) {
        Assertion.check().isNotNull(redisConnector);
        this.redisConnector = redisConnector;
    }

    public void storeValue(final String key, final String value) {
        // RedisConnector returns a pool-shared instance — do NOT close it!
        redisConnector.getClient().set(key, value);
    }
}
```

> ⚠️ **Important — Redis**: `getClient()` returns a shared pool instance.
> **Never wrap it in `try-with-resources`**, as that would close the pool.
> Use `getClient(String key)` for single-connection operations that require `try-with-resources`.

> ✅ **HttpClient**: `getClient()` creates a fresh `HttpClient` each time (factory pattern).
> You may safely use it however you like.

---

## 6. How to Create a New Connector

### 6.1 Maven module

```xml
<!-- vertigo-connectors/pom.xml — add to <modules> -->
<module>vertigo-mytech-connector</module>
```

```xml
<!-- vertigo-mytech-connector/pom.xml -->
<parent>
    <groupId>io.vertigo</groupId>
    <artifactId>vertigo-connectors</artifactId>
    <version>5.0.0-SNAPSHOT</version>
</parent>
<artifactId>vertigo-mytech-connector</artifactId>
<dependencies>
    <dependency>
        <groupId>com.mytech</groupId>
        <artifactId>mytech-client</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 6.2 Connector implementation

```java
package io.vertigo.connectors.mytech;

import jakarta.inject.Inject;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

import java.util.Optional;

/**
 * Connector for MyTech service.
 */
public final class MyTechConnector implements Connector<MyTechClient>, Activeable {

    private final String connectorName;
    private final MyTechClient client;

    @Inject
    public MyTechConnector(
            @ParamValue("name")     final Optional<String> connectorNameOpt,
            @ParamValue("host")     final String host,
            @ParamValue("port")     final int port,
            @ParamValue("password") final Optional<String> passwordOpt) {
        Assertion.check()
                .isNotBlank(host)
                .isTrue(port > 0 && port < 65536, "port must be between 1 and 65535");
        //---
        connectorName = connectorNameOpt.orElse("main");
        final var builder = MyTechClient.builder().host(host).port(port);
        passwordOpt.ifPresent(builder::password);
        client = builder.build();
    }

    @Override
    public MyTechClient getClient() {
        return client;
    }

    @Override
    public String getName() {
        return connectorName;
    }

    @Override
    public void start() {
        // optional: warm-up, ping, …
    }

    @Override
    public void stop() {
        client.close();
    }
}
```

### 6.3 Features class

```java
package io.vertigo.connectors.mytech;

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

public final class MyTechFeatures extends Features<MyTechFeatures> {

    public MyTechFeatures() {
        super("vertigo-mytech-connector");
    }

    @Feature("mytech")
    public MyTechFeatures withMyTech(final Param... params) {
        getModuleConfigBuilder().addConnector(MyTechConnector.class, params);
        return this;
    }

    @Override
    protected void buildFeatures() {
        // place mandatory (auto-registered) components here
    }
}
```

### 6.4 Test

```java
package io.vertigo.connectors.mytech;

import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import io.vertigo.core.node.AutoCloseableNode;
import io.vertigo.core.node.component.di.DIInjector;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.param.Param;

class MyTechConnectorTest {

    @Inject
    private MyTechConnector connector;
    private AutoCloseableNode node;

    @BeforeEach
    void setUp() {
        node = new AutoCloseableNode(buildNodeConfig());
        DIInjector.injectMembers(this, node.getComponentSpace());
    }

    @AfterEach
    void tearDown() {
        if (node != null) node.close();
    }

    @Test
    void testConnection() {
        // ping or trivial operation that proves the connection is live
        Assertions.assertNotNull(connector.getClient().ping());
    }

    private static NodeConfig buildNodeConfig() {
        return NodeConfig.builder()
                .addModule(new MyTechFeatures()
                        .withMyTech(
                                Param.of("host", "localhost"),
                                Param.of("port", "1234"))
                        .build())
                .build();
    }
}
```

---

## 7. Configuration Reference

### Redis (`RedisFeatures.withJedis`)

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | String | No | `"main"` | Connector logical name |
| `host` | String | Single-node | — | Redis host (single mode) |
| `port` | int | Single-node | — | Redis port (single mode) |
| `sentinels` | String | Sentinel | — | `"host1:port1;host2:port2"` |
| `mastername` | String | Sentinel | — | Sentinel master name |
| `clusterNodes` | String | Cluster | — | `"host1:port1;host2:port2"` |
| `database` | int | No | `0` | Redis database index (0-15, must be 0 for cluster) |
| `password` | String | No | — | Redis AUTH password |
| `ssl` | boolean | No | `false` | Enable SSL/TLS |
| `trustStoreUrl` | String | No | — | PKCS12 trust store classpath URL |
| `trustStorePassword` | String | No | — | Trust store password |
| `maxTotal` | int | No | Jedis default | Max connections in pool |
| `minIdle` | int | No | Jedis default | Min idle connections |

> Modes are **mutually exclusive**: supply exactly one of `host`/`port`, `sentinels`, or `clusterNodes`.

---

### HTTP Client (`HttpClientFeatures.withHttpClient`)

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | String | No | `"main"` | Connector logical name |
| `urlPrefix` | String | Yes | — | Base URL (`https://api.example.com`) — no trailing `/` |
| `connectTimeoutSecond` | int | No | `20` | Connection timeout in seconds |
| `proxy` | String | No | — | Proxy hostname |
| `proxyPort` | int | If proxy | — | Proxy port |
| `trustStoreUrl` | String | No | — | PKCS12 trust store |
| `trustStorePassword` | String | No | — | Trust store password |

---

### MongoDB (`MongodbFeatures.withMongoClient`)

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | String | No | Connector logical name |
| `connectionString` | String | Yes | Full MongoDB connection string (see [Connection String URI](https://www.mongodb.com/docs/manual/reference/connection-string/)) |

---

### Neo4j (`Neo4jFeatures.withNeo4j`)

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | String | No | Connector logical name |
| `uri` | String | Yes | Bolt URI, e.g. `bolt://localhost:7687` |
| `login` | String | Yes | Neo4j username |
| `password` | String | Yes | Neo4j password |

---

### InfluxDB (`InfluxDbFeatures.withInfluxDb`)

| Parameter | Type | Required | Description |
|---|---|---|---|
| `name` | String | No | Connector logical name |
| `host` | String | Yes | InfluxDB URL |
| `token` | String | Yes | API token |
| `org` | String | Yes | Organisation name |

---

## 8. SSL / TLS Configuration

All connectors that support SSL use the same pattern: a **PKCS12 trust store** loaded from the classpath.

### Shared SSL setup

```java
// In connector constructor (pattern used by Redis, HttpClient, Elasticsearch, …)
private static SSLSocketFactory createTrustStoreSslSocketFactory(
        final URL trustStoreUrl,
        final String trustStorePassword) throws Exception {
    final var trustStore = KeyStore.getInstance("pkcs12");
    try (var inputStream = trustStoreUrl.openStream()) {
        trustStore.load(inputStream, trustStorePassword.toCharArray());
    }
    final var trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);
    final var sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
    return sslContext.getSocketFactory();
}
```

### Enabling SSL in configuration

```java
new RedisFeatures()
    .withJedis(
        Param.of("host",               "redis.example.com"),
        Param.of("port",               "6380"),
        Param.of("ssl",                "true"),
        Param.of("trustStoreUrl",      "classpath:/certs/redis-truststore.p12"),
        Param.of("trustStorePassword", "changeit"),
        Param.of("database",           "0"))
    .build()
```

> **Note**: The SSL context creation code is currently duplicated in several connectors.
> See [Proposed Improvements](#10-proposed-improvements) for the recommended refactoring.

---

## 9. Testing

### Test strategy

Tests are **integration tests**: they connect to a real running service (Docker).
All current tests target `docker-vertigo.part.klee.lan.net` as the default host.

### Running tests

```bash
# Run all tests (requires Docker services running)
mvn test

# Run tests for a single module
mvn test -pl vertigo-redis-connector

# Skip integration tests
mvn test -DskipTests
```

### Test results (current state)

| Module | Test file | Result | Notes |
|---|---|---|---|
| `vertigo-redis-connector` | `RedisConnectorTest` | ✅ PASSED (~9 s) | Requires Redis at `docker-vertigo:6379` |
| `vertigo-redis-connector` | `RedisSingleConnectorTest` | ✅ PASSED (<1 s) | Requires Redis at `docker-vertigo:6379` |
| `vertigo-neo4j-connector` | `EmbeddedNeo4JConnectorTest` | ✅ PASSED (~91 s) | Embedded server, self-contained |
| `vertigo-mongodb-connector` | `MongoClientConnectorTest` | ❌ ERROR | MongoDB at `docker-vertigo:27017` unreachable |
| 17 other modules | — | — | **No tests** |

> `BUILD SUCCESS` is reported despite the MongoDB error because surefire has `testFailureIgnore=true` (inherited from `vertigo-parent`).

### Writing a new test

Follow the canonical pattern:

```java
class MyConnectorTest {

    @Inject
    private MyConnector connector;
    private AutoCloseableNode node;

    @BeforeEach
    void setUp() {
        node = new AutoCloseableNode(buildNodeConfig());
        DIInjector.injectMembers(this, node.getComponentSpace());
    }

    @AfterEach
    void tearDown() {
        if (node != null) node.close();
    }

    @Test
    void testConnection() {
        // 1. Obtain client
        final var client = connector.getClient();
        // 2. Execute a trivial operation (ping, list, count …)
        Assertions.assertNotNull(client);
    }

    private static NodeConfig buildNodeConfig() {
        return NodeConfig.builder()
                .addModule(new MyFeatures()
                        .withMyConnector(
                                Param.of("host", "localhost"),
                                Param.of("port", "9999"))
                        .build())
                .build();
    }
}
```

---

## 10. Proposed Improvements

### P1 — Extract shared SSL utility

**Problem**: `createTrustStoreSslSocketFactory` / `createTrustStoreSslContext` is copy-pasted verbatim in at least **4 connectors** (Redis, HttpClient, Elasticsearch, LDAP).

**Proposal**: Create a shared `SslContextFactory` utility class in a dedicated `vertigo-connector-commons` module (or in `vertigo-core` if it fits):

```java
// io.vertigo.connectors.commons.SslContextFactory
public final class SslContextFactory {
    private SslContextFactory() {}

    public static SSLContext fromPkcs12(final URL trustStoreUrl, final String password) {
        final var trustStore = KeyStore.getInstance("pkcs12");
        try (var in = trustStoreUrl.openStream()) {
            trustStore.load(in, password.toCharArray());
        }
        final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        final var ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }
}
```

---

### P2 — Remove deprecated `RedisSingleConnector`

**Problem**: `RedisSingleConnector` is annotated `@Deprecated` but still ships.
`RedisConnector` in single-node mode covers all its use cases.

**Proposal**: Remove `RedisSingleConnector` and `RedisFeatures.withJedisSingle(...)` in the next major version. Add a migration note to CHANGES.md.

---

### P3 — Standardise parameter naming

**Problem**: Configuration parameter names are inconsistent across modules:

| Module | Pattern |
|---|---|
| Redis | `host`, `port` (single values) |
| Redis | `sentinels` = `"h1:p1;h2:p2"` (`;`-separated) |
| Redis | `clusterNodes` = `"h1:p1;h2:p2"` (`;`-separated) |
| Elasticsearch | `servers.names` = `"h1:p1,h2:p2"` (`,`-separated) |

**Proposal**: Adopt a single convention for multi-value host lists:
`"host1:port1;host2:port2"` (semicolon-separated) everywhere.
Rename `clusterNodes` → `nodes` for brevity and consistency.

---

### P4 — Externalise Docker host to system property in tests

**Problem**: Tests hard-code `docker-vertigo.part.klee.lan.net`.
This prevents running tests locally or in a CI environment with a different Docker host.

**Proposal**: Read the host from a system property with a local fallback:

```java
private static final String DOCKER_HOST = System.getProperty("vertigo.test.docker.host", "localhost");

private static NodeConfig buildNodeConfig() {
    return NodeConfig.builder()
            .addModule(new RedisFeatures()
                    .withJedis(
                            Param.of("host", DOCKER_HOST),
                            Param.of("port", "6379"),
                            …)
                    .build())
            .build();
}
```

Run with: `mvn test -Dvertigo.test.docker.host=my-docker-host`

---

### P5 — Add `@Tag("integration")` and skip by default

**Problem**: Running `mvn test` without services fails silently (errors, not failures). This pollutes CI output.

**Proposal**: Annotate all connector tests with JUnit 5's `@Tag("integration")` and exclude them by default:

```java
@Tag("integration")
class RedisConnectorTest { … }
```

```xml
<!-- pom.xml in vertigo-connectors -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>integration</excludedGroups>
    </configuration>
</plugin>
```

Run integration tests explicitly:
```bash
mvn test -Dgroups=integration -Dvertigo.test.docker.host=localhost
```

---

### P6 — Add missing tests (priority order)

| Module | Effort | Notes |
|---|---|---|
| `vertigo-httpclient-connector` | Low | Mock HTTP server with `MockWebServer` |
| `vertigo-mqtt-connector` | Medium | Embedded MQTT broker (Moquette) |
| `vertigo-spring-connector` | Medium | Spring context instantiation |
| `vertigo-influxdb-connector` | Medium | Docker InfluxDB |
| `vertigo-ldap-connector` | Medium | Embedded LDAP (UnboundID) |
| `vertigo-elasticsearch-connector` | High | Docker Elasticsearch |

---

### P7 — Clarify Elasticsearch version strategy

**Problem**: Two modules (`vertigo-elasticsearch-connector` and `vertigo-elasticsearch_7_17-connector`) target the same ES 7.17 line, with unclear maintenance boundaries.

**Proposal**: Document in each module's README which ES server versions it supports. Consider consolidating into a single module with version-specific connector variants via `@Feature` annotations.

---

### P8 — Align `HttpClientConnector` with `Activeable`

**Problem**: `HttpClientConnector` does **not** implement `Activeable`, unlike most connectors that hold state. While `HttpClient` is stateless here (factory pattern), this inconsistency can confuse developers who expect all connectors to expose lifecycle methods.

**Proposal**: Either document explicitly "this connector is stateless — `Activeable` not needed" or add a no-op `Activeable` implementation for consistency.

---

### P9 — Add `@NonnullParameterValidator` to connectors

**Problem**: Parameter validation is performed manually in each constructor with verbose `Assertion.check()` chains.

**Proposal**: Use the new `NonnullParameterValidator` from `vertigo-core` (see `io.vertigo.core.node.component.validation`) to validate `@Nonnull`-annotated parameters automatically. This reduces boilerplate while keeping the fail-fast guarantee.

```java
// Before
Assertion.check()
    .isNotBlank(host)
    .isTrue(port > 0, "port must be positive");

// After — annotation-driven, validated by NonnullParameterValidator
public MyConnector(
    @Nonnull @ParamValue("host") final String host,
    @ParamValue("port") final int port) { … }
```

---

*Generated by Claude Code — February 2026*
