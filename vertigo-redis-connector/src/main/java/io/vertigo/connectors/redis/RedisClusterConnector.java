/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2023, Vertigo.io, team@vertigo.io
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
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Activeable;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.util.JedisClusterCRC16;

/**
 * @author pchretien, npiedeloup
 */
public class RedisClusterConnector implements Connector<JedisCluster>, Activeable {

	private static final Logger LOG = LogManager.getLogger(RedisClusterConnector.class);

	private static final int MAX_ATTEMPTS = 5;
	private static final int CONNECT_TIMEOUT = 2000;
	private final JedisCluster jedisCluster;
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
	public RedisClusterConnector(
			final ResourceManager resourceManager,
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clusterNodes") final String clusterNodesStr,
			@ParamValue("database") final int redisDatabase,
			@ParamValue("password") final Optional<String> passwordOpt,
			@ParamValue("ssl") final boolean ssl,
			@ParamValue("trustStoreUrl") final Optional<String> trustStoreUrlOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt,
			@ParamValue("maxTotal") final Optional<Integer> maxTotalOpt,
			@ParamValue("minIdle") final Optional<Integer> minIdleOpt) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clusterNodesStr)
				.isNotNull(passwordOpt)
				.isTrue(redisDatabase >= 0 && redisDatabase < 16, "there is 16 DBs(0 - 15); your index database '{0}' is not inside this range", redisDatabase);
		//-----
		connectorName = connectorNameOpt.orElse("main");

		final var connectionPoolConfig = new ConnectionPoolConfig();
		maxTotalOpt.ifPresent(maxTotal -> {
			connectionPoolConfig.setMaxTotal(maxTotal);
			connectionPoolConfig.setMaxIdle(maxTotal);
		});
		minIdleOpt.ifPresent(connectionPoolConfig::setMinIdle);
		connectionPoolConfig.setMaxWait(Duration.ofSeconds(5));

		final var jedisClientConfigBuilder = DefaultJedisClientConfig.builder()
				.connectionTimeoutMillis(CONNECT_TIMEOUT)
				.database(redisDatabase)
				.ssl(ssl);
		passwordOpt.ifPresent(jedisClientConfigBuilder::password);

		if (trustStoreUrlOpt.isPresent()) {
			try {
				final var sslSocketFactory = createTrustStoreSslSocketFactory(resourceManager.resolve(trustStoreUrlOpt.get()), trustStorePasswordOpt.get());
				final var sslParameters = new SSLParameters();
				jedisClientConfigBuilder
						.sslParameters(sslParameters)
						.sslSocketFactory(sslSocketFactory);
			} catch (final Exception e) {
				throw WrappedException.wrap(e);
			}
		}
		final JedisClientConfig jedisClientConfig = jedisClientConfigBuilder.build();
		final Set<HostAndPort> clusterNodes = Set.of(clusterNodesStr.split(";")).stream().map(HostAndPort::from).collect(Collectors.toSet());
		jedisCluster = new JedisCluster(clusterNodes, jedisClientConfig, MAX_ATTEMPTS, connectionPoolConfig);

		//test
		jedisCluster.ping();
	}

	/**
	 * @return jedisCluster client (DON't USE try-with-ressource pattern)
	 */
	@Override
	public JedisCluster getClient() {
		if (jedisCluster.getClusterNodes().isEmpty()) {
			LOG.warn("JedisCluster : no nodes found. JedisCluster shouldn't be close manually (watch out try-with-ressource pattern), it can't be reused after that. it will be close correctly at app closing. Try to rediscover nodes (cost a lot)");
			jedisCluster.getConnectionFromSlot(0);
		}

		return jedisCluster;
	}

	/**
	 * @param key Key used to find slot, may use Hash-tags of Redis and use partition key between {}.
	 * Ex: myPrefix-{myPartioningKey}-myFonctionalKey. Warning : only first {} is used !
	 * @see https://redis.io/docs/reference/cluster-spec/#hash-tags
	 * @return jedis client for one node (use try-with-resource pattern)
	 */
	public Jedis getClient(final String key) {
		final int slot = JedisClusterCRC16.getSlot(key);
		return new Jedis(jedisCluster.getConnectionFromSlot(slot));
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
		jedisCluster.close();
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
