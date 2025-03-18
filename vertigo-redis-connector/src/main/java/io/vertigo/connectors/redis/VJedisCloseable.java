package io.vertigo.connectors.redis;

public interface VJedisCloseable {

	/**
	 * Close super jedis unified connection.
	 */
	void closeJedisUnified();
}
