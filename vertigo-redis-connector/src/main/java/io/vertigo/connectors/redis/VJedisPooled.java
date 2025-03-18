package io.vertigo.connectors.redis;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.providers.PooledConnectionProvider;

/**
 * Extends JedisPooled, to override close methods.
 * JedisPooled is AutoCloseable, but we don't want to close pool every times.
 */
class VJedisPooled extends JedisPooled implements VJedisCloseable {

	public VJedisPooled(final PooledConnectionProvider pooledConnectionProvider) {
		super(pooledConnectionProvider);
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
