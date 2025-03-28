Version history
===============

Running 5.0.0
----------------------
more to come :)


Release 4.3.0 - 2025/03/27
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-420-to-430)
* [OIDC] `scopes` and `externalUrl` parameters moved to `OIDCWebAuthenticationPlugin`
* [OIDC] `loginLocaleParamName` renamed to `localeParamNameOpt` and used on logout too
* [OIDC] `OIDCDeploymentConnector.getClient()` now return an usable client (`OIDCClient`) instead of a record of parameters
* [S3] Add region parameter (mandatory for some CSP)
* [Jsch] Better error message when missing key
* [Mail] Fix port configuration (property `mail.smtp.port` instead of `mail.port`)
* [Redis] Wrap unifiedJedis to close connexions only when closing the pool
* Update libs
 - azure msal4j 1.16.0 -> 1.19.1
 - elasticsearch 7.17.14 -> 7.17.28
 - influxdb 7.1.0 -> 7.2.0
 - javalin 6.1.6 -> 6.5.0
 - jetty 11.0.21 -> 11.0.25
 - bouncycastle bcpkix-jdk18on 1.78.1 -> 1.80
 - keycloak-servlet-filter-adapter 24.0.5 -> 25.0.3
 - mongodb -> 5.1.1 -> 5.4.0
 - neo4j 5.21.0 -> 5.25.1
 - neo4j-driver 5.22.0 -> 5.26.3
 - oidc nimbusds 11.13 -> 11.23.1
 - jedis 5.1.3 -> 5.2.0
 - s3 8.5.11 -> 8.5.17
 - opensaml 5.1.2 -> 5.1.3
 - fasterxml 6.7.0 -> 7.1.0
 - spring-context 6.1.10 -> 6.2.5

Release 4.2.0 - 2024/07/04
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-410-to-420)

* **[All] Pom declare dependencyManagment to help dependencies versions config**
* [Redis] Add analytics tag `hasLock`
* [OIDC] Add `trustStore` optional parameter
* [OIDC] Change `scopes` parameter to optional
* [OIDC] Add parameters for logout redirect
* [OIDC] Add `overrideIssuer` parameter. Usefull if your SSO uses his external url as issuer and your backchannel is by another URL.
* [OIDC] Add `loginLocaleParamName` parameter to forward user locale into login url parameter
* [Keycloak] Remove keycloak connector (deprecated)
* [S3] Add object storage S3 connector using minio lib
* Update libs 
 - azure msal4j 1.14.0 -> 1.16.0
 - elasticsearch 7.17.14 -> 7.17.22
 - codelibs.elasticsearch 7.17.9 -> 7.17.22
 - influxdb 6.10.0 -> 7.1.0
 - javalin 5.6.3 -> 6.1.6
 - jetty 11.0.18 -> 11.0.21
 - jsch com.jcraft 0.1.55 -> github.mwiede 0.2.18
 - bouncycastle bcpkix-jdk15on 1.70 ->bcpkix-jdk18on 1.78.1
 - keycloak-servlet-filter-adapter 22.0.5 -> 24.0.5
 - mail -> javax.mail 1.6.2 -> jakarta.mail 2.0.1
 - mongodb -> 4.11.0 -> 5.1.1
 - minio 8.5.10 -> 8.5.11
 - neo4j 5.13.0 -> 5.21.0
 - oidc nimbusds 11.5 -> 11.13
 - jedis 4.4.6 -> 5.1.3
 - opensaml 4.3.0 -> 5.1.2
 - woodstox 6.5.1 -> 6.7.0
 - spring-context 6.0.13 -> 6.1.10


Release 4.1.0 - 2023/11/09
----------------------
* [Redis] Extends Redis Unified connector to all mode : single, sentinels and cluster
* [Redis] Support ssl and custom truststore
* [Redis] Add util for lock using redis
* [Elastic] Add security to RHL ES connector (username/password ; apiKeyId/apiKeySecret ; ssl + truststore/truststorePassword)
  * **ssl parameter is mandatory**
* [Javalin] Protect jetty mutlipart cleaner
* [OIDC] Add support for PKCE (enabled by default)
* Update libs 
  - azure 1.13.9 -> 1.14.0
  - elasticsearch 7.17.12 -> 7.17.14
  - influxdb 6.9.0 -> 6.10.0
  - javalin 5.6.1 -> 5.6.3
  - jetty 11.0.15 -> 11.0.18
  - mongodb 4.10.2 -> 4.11.0
  - neo4j 5.10.0 -> 5.13.0
  - oidc 10.11 -> 11.5
  - redis 4.4.3 -> 4.4.3
  - spring 6.0.11 -> 6.0.13
  - keycloak 21.1.1 -> 22.0.5


Release 4.0.0 - 2023/08/17
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-360-to-400)
* [keycloak] remove deprecated connector (https://www.keycloak.org/2022/02/adapter-deprecation)
* **[ui, vega] Update jakarta namespace ( Spring 6, Javalin 5, Jetty 11 )**
* [http-client] support custom trust store
* [ifttt] remove jersey dependency
* **[Javalin] Add security limit of request size to javalin maxRequestSize (default 10Ko)**
* **[Javalin] Add sniHostCheck param** for embedded server (active by default with jetty but need to inactivate when behind a proxy)
* [Redis] Add Redis Unified connector (for cluster)
* [Elastic] Add LTS connector for v7.17 (prepare update to 8.x)
* Updated libs
  - azure 1.13.8 -> 1.13.9
  - elasticsearch 7.17.6 -> 7.17.12
  - influxdb 6.8.0 -> 6.9.0
  - javalin 5.4.2 -> 5.6.1
  - jetty-server 9.4.51 -> 11.0.15
  - mongodb 4.9.1 -> 4.10.2
  - neo4j 4.4.16 -> 5.10.0 (jdk17)
  - woodstox 6.5.0 -> 6.5.1
  - spring-context 5.3.27 -> 6.0.11
  - oauth2-oidc-sdk : 10.8 -> 10.11
  - jedis : 4.3.2 -> 4.4.3


Release 3.6.0 - 2023/05/04
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-350-to-360)
* [http-client] support custom trust store
* Updated libs
  - msal4j : 1.13.3 -> 1.13.8
  - elasticsearch : 7.17.8 -> 7.17.9
  - influxdb-client-java : 6.7.0 -> 6.8.0
  - keycloak-servlet-filter-adapter :  20.0.2 -> 21.1.1
  - mongodb : 4.8.1 -> 4.9.1
  - neo4j : 4.4.16 -> 4.4.20
  - oauth2-oidc-sdk : 10.4 -> 10.8
  - jedis : 4.3.1 -> 4.3.2
  - opensaml-saml-impl : 4.2.0 -> 4.3.0
  - spring : 5.3.24 -> 5.3.27
 

Release 3.5.0 - 2023/01/06
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-340-to-350)
* Updated libs
  - azure msal4j 1.13.1 -> 1.13.3
  - elasticsearch 7.17.6 -> 7.17.8
  - jersey 3.0.8 -> 3.1.0
  - influxdb-client-java 6.5.0 -> 6.7.0
  - javalin 4.6.4 -> 4.6.7
  - jetty-server 9.4.49.v20220914 -> 9.4.50.v20221201
  - keycloak-servlet-filter-adapter 19.0.2
  - bcpkix-jdk15on 1.64 -> 1.70
  - keycloak 19.0.2 -> 20.0.2
  - oidc 9.43.1 -> 10.4
  - mongodb 4.7.1 -> 4.8.1
  - neo4j 4.4.11 -> 4.4.16
  - neo4j-java-driver 4.4.9 -> 4.4.11
  - jedis 4.2.3 -> 4.3.1
  - opensaml-saml-impl 4.0.1 -> 4.2.0
  - spring-context 5.3.23 -> 5.3.24
  - twitter 4.0.7 -> 4.1.2
  

Release 3.4.0 - 2022/10/12
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-330-to-340)
* Add jsch connector
* Add saml2 connector
* Add oidc connector
* Updated libs
  - azure msal4j 1.11.0 -> 1.13.1
  - elasticsearch 7.16.3 -> 7.17.6
  - jersey 3.0.3 -> 3.0.8
  - influxdb-client-java 4.0.0 -> 6.5.0
  - javalin 4.3.0 -> 4.6.4
  - keycloak-servlet-filter-adapter 16.1.0 -> 19.0.2
  - mongodb 4.4.1 -> 4.7.1
  - neo4j 4.4.3 -> 4.4.11
  - neo4j-java-driver 4.4.2 -> 4.4.9
  - jedis 4.0.1 -> 4.2.3
  - spring-context 5.3.15 -> 5.3.23

Release 3.3.0 - 2022/02/03
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-320-to-330)
* Add Azure connector
* Updated libs
  - elasticsearch 7.10.2 -> 7.16.3
  - jersey 2.34 -> 3.0.3
  - influxdb-client-java 2.2.0 -> 4.0.0
  - javalin 3.13.7 -> 4.3.0
  - keycloak-servlet-filter-adapter 13.0.1 -> 16.1.0
  - mongodb 4.2.3 -> 4.4.1
  - neo4j 4.2.7 -> 4.4.3 
  - neo4j-java-driver 4.3.0 -> 4.4.2
  - jedis 3.6.0 -> 4.0.1
  - spring-context 5.3.7 -> 5.3.15
  
Release 3.2.0 - 2021/06/21
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-311-to-320)
* Add HttpClient connector
* Switch to influxDb 2
* Updated libs
  - influxdb-client-java 2.21 -> 2.2.0
  - javalin 3.13.3 -> 3.13.7
  - keycloak-servlet-filter-adapter 12.0.2 -> 13.0.1
  - mongodb 4.2.0 -> 4.2.3
  - neo4j 4.1.3 -> 4.2.7
  - neo4j-java-driver 4.1.1 -> 4.3.0
  - jedis 3.5.1 -> 3.6.0
  - spring-context 5.3.3 -> 5.3.7

Release 3.1.0 - 2021/02/05
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-300-to-310)
* Updated libs
  - elasticsearch 7.9.3 -> 7.10.2
  - jersey 2.32 -> 2.33
  - influxdb 2.20 -> 2.21
  - javalin 3.12.0 -> 3.13.3
  - keycloak-servlet-filter-adapter 11.0.3 -> 12.0.2
  - mongodb 4.1.1 -> 4.2.0
  - jedis 3.3.0 -> 3.5.1
  - spring-context 5.3.0 -> 5.3.3
  
  

Release 3.0.0 - 2020/11/20
----------------------
[Migration help](https://github.com/vertigo-io/vertigo/wiki/Vertigo-Migration-Guide#from-210-to-300)
 * First release


