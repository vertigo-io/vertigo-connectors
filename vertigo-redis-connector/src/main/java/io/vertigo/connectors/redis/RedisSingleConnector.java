/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2025, Vertigo.io, team@vertigo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertigo.connectors.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.SSLParameters;

import io.vertigo.connectors.ssl.ConnectorSslUtil;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
import jakarta.inject.Inject;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.util.Pool;

/**
 * @author pchretien
 */
@Deprecated
public class RedisSingleConnector implements Connector<Jedis>, Activeable {
	private static final int CONNECT_TIMEOUT = 2000;
	private final Pool<Jedis> jedisPool;
	private final String connectorName;

	/**
	 * Constructor.
	 * @param connectorNameOpt name of the connector (main by default)
	 * @param redisHost REDIS server host name
	 * @param redisPort REDIS server port
	 * @param redisDatabase REDIS database index
	 * @param passwordOpt password (optional)
	 */
	@Inject
	public RedisSingleConnector(
			final ResourceManager resourceManager,
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("host") final String redisHost,
			@ParamValue("port") final int redisPort,
			@ParamValue("database") final int redisDatabase,
			@ParamValue("password") final Optional<String> passwordOpt,
			@ParamValue("ssl") final boolean ssl,
			@ParamValue("mastername") final Optional<String> masternameOpt,
			@ParamValue("sentinels") final Optional<String> sentinelsOpt,
			@ParamValue("trustStoreUrl") final Optional<String> trustStoreUrlOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt,
			@ParamValue("maxTotal") final Optional<Integer> maxTotalOpt,
			@ParamValue("minIdle") final Optional<Integer> minIdleOpt) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(redisHost)
				.isNotNull(passwordOpt)
				.isTrue(redisDatabase >= 0 && redisDatabase < 16, "there is 16 DBs(0 - 15); your index database '{0}' is not inside this range", redisDatabase);
		//-----
		connectorName = connectorNameOpt.orElse("main");

		final var jedisPoolConfig = new JedisPoolConfig();
		maxTotalOpt.ifPresent(maxTotal -> {
			jedisPoolConfig.setMaxTotal(maxTotal);
			jedisPoolConfig.setMaxIdle(maxTotal);
		});
		minIdleOpt.ifPresent(jedisPoolConfig::setMinIdle);
		jedisPoolConfig.setMaxWait(Duration.ofSeconds(5));

		final var jedisClientConfigBuilder = DefaultJedisClientConfig.builder()
				.connectionTimeoutMillis(CONNECT_TIMEOUT)
				.database(redisDatabase)
				.ssl(ssl);
		final var sentinelConfigBuilder = DefaultJedisClientConfig.builder()
				.connectionTimeoutMillis(CONNECT_TIMEOUT)
				.ssl(ssl);
		passwordOpt.ifPresent(jedisClientConfigBuilder::password);

		if (trustStoreUrlOpt.isPresent()) {
			final var sslSocketFactory = ConnectorSslUtil.buildSslSocketFactory(resourceManager.resolve(trustStoreUrlOpt.get()), trustStorePasswordOpt.get());
			final var sslParameters = new SSLParameters();
			jedisClientConfigBuilder
					.sslParameters(sslParameters)
					.sslSocketFactory(sslSocketFactory);

			sentinelConfigBuilder
					.sslParameters(sslParameters)
					.sslSocketFactory(sslSocketFactory);
		}
		final JedisClientConfig jedisClientConfig = jedisClientConfigBuilder.build();
		if (sentinelsOpt.isPresent()) {
			final Set<HostAndPort> sentinels = Set.of(sentinelsOpt.get().split(";")).stream().map(HostAndPort::from).collect(Collectors.toSet());
			jedisPool = new JedisSentinelPool(masternameOpt.get(), sentinels, jedisPoolConfig, jedisClientConfig, sentinelConfigBuilder.build());
		} else {
			jedisPool = new JedisPool(jedisPoolConfig, new HostAndPort(redisHost, redisPort), jedisClientConfig);
		}
		//test
		try (var jedis = jedisPool.getResource()) {
			jedis.ping();
		}
	}

	/**
	 * @return jedis client
	 */
	@Override
	public Jedis getClient() {
		return jedisPool.getResource();
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/** {@inheritDoc} */
	@Override
	public void start() {
		//
	}

	/** {@inheritDoc} */
	@Override
	public void stop() {
		jedisPool.close();
	}

}
