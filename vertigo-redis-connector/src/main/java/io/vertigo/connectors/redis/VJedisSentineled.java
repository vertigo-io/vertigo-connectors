package io.vertigo.connectors.redis;

import redis.clients.jedis.JedisSentineled;
import redis.clients.jedis.providers.SentineledConnectionProvider;

/**
 * Extends JedisSentineled, to override close methods.
 * JedisSentineled is AutoCloseable, but we don't want to close connections every times.
 */
class VJedisSentineled extends JedisSentineled implements VJedisCloseable {

	public VJedisSentineled(final SentineledConnectionProvider sentineledConnectionProvider) {
		super(sentineledConnectionProvider);
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
