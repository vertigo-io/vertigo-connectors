package io.vertigo.connectors.redis;

import java.time.Duration;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.providers.ClusterConnectionProvider;

/**
 * Extends JedisCluster, to override close methods.
 * JedisCluster is AutoCloseable, but we don't want to close cluster every times.
 */
class VJedisCluster extends UnifiedJedis implements VJedisCloseable {

	public VJedisCluster(final ClusterConnectionProvider clusterConnectionProvider, final int maxAttempts, final Duration ofMillis) {
		super(clusterConnectionProvider, maxAttempts, ofMillis);
	}

	@Override
	public void close() {
		//nothing here
	}

	@Override
	public void closeJedisUnified() {
		super.close();
	}

}
