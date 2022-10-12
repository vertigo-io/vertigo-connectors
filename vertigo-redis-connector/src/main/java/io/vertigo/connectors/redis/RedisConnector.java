/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2022, Vertigo.io, team@vertigo.io
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

import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
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
public class RedisConnector implements Connector<Jedis>, Activeable {
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
	public RedisConnector(
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
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(redisHost)
				.isNotNull(passwordOpt)
				.isTrue(redisDatabase >= 0 && redisDatabase < 16, "there 16 DBs(0 - 15); your index database '{0}' is not inside this range", redisDatabase);
		//-----
		connectorName = connectorNameOpt.orElse("main");

		final var jedisPoolConfig = new JedisPoolConfig();

		final var jedisClientConfigBuilder = DefaultJedisClientConfig.builder()
				.connectionTimeoutMillis(CONNECT_TIMEOUT)
				.database(redisDatabase)
				.ssl(ssl);
		final var sentinelConfigBuilder = DefaultJedisClientConfig.builder()
				.connectionTimeoutMillis(CONNECT_TIMEOUT)
				.ssl(ssl);
		passwordOpt.ifPresent(jedisClientConfigBuilder::password);

		if (trustStoreUrlOpt.isPresent()) {
			try {
				final var sslSocketFactory = createTrustStoreSslSocketFactory(resourceManager.resolve(trustStoreUrlOpt.get()), trustStorePasswordOpt.get());
				final var sslParameters = new SSLParameters();
				jedisClientConfigBuilder
						.sslParameters(sslParameters)
						.sslSocketFactory(sslSocketFactory);

				sentinelConfigBuilder
						.sslParameters(sslParameters)
						.sslSocketFactory(sslSocketFactory);
			} catch (final Exception e) {
				throw WrappedException.wrap(e);
			}
		}
		final JedisClientConfig jedisClientConfig = jedisClientConfigBuilder.build();
		if (sentinelsOpt.isPresent()) {
			final Set<HostAndPort> sentinels = Set.of(sentinelsOpt.get().split(";")).stream().map(HostAndPort::from).collect(Collectors.toSet());
			jedisPool = new JedisSentinelPool(masternameOpt.get(), sentinels, jedisClientConfig, sentinelConfigBuilder.build());
		} else {
			jedisPool = new JedisPool(jedisPoolConfig, new HostAndPort(redisHost, redisPort), jedisClientConfig);
		}
		//test
		try (var jedis = jedisPool.getResource()) {
			jedis.ping();
		}
	}

	/**
	 * @return Redis resource
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
		jedisPool.destroy();
	}

	private static SSLSocketFactory createTrustStoreSslSocketFactory(final URL trustStoreUrl, final String trustStorePassword) throws Exception {
		final var trustStore = KeyStore.getInstance("pkcs12");
		try (var inputStream = trustStoreUrl.openStream()) {
			trustStore.load(inputStream, trustStorePassword.toCharArray());
		}

		final var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);
		final var trustManagers = trustManagerFactory.getTrustManagers();

		final var sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, trustManagers, new SecureRandom());
		return sslContext.getSocketFactory();
	}

}
