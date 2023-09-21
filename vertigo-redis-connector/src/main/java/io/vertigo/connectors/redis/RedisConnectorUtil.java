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

import java.util.List;

import io.vertigo.core.lang.Assertion;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

/**
 * @author pchretien, npiedeloup
 */
public final class RedisConnectorUtil {
	private static final String REDIS_INCR_EXPIRE_SCRIPT = "local newCount = redis.call('INCR', KEYS[1]) "
			+ "if (tonumber(newCount) == 1) then "
			+ "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
			+ "end "
			+ "return newCount";

	private RedisConnectorUtil() {
		//util class : private constructor
	}

	public static boolean obtainLockScript(final UnifiedJedis jedis, final String lockName, final int lockTimeOutSecond) {
		Assertion.check().isNotNull(jedis)
				.isNotBlank(lockName)
				.isTrue(lockName.endsWith(".lock"), "Lock name, must ends with '.lock' ({0})", lockName);
		//----
		final Long lock = (Long) jedis.eval(REDIS_INCR_EXPIRE_SCRIPT, List.of(lockName), List.of(String.valueOf(lockTimeOutSecond)));
		if (lock == 1) { //we got lock
			return true;
		}
		//if we don't get lock, we check TTL was really put (in case of crash of locker)
		final long lockTTL = jedis.ttl(lockName);
		if (lockTTL == -1) {
			//No lock and no TTL : something's wrong, shouldn't happend with INCR_EXPIRE_SCRIPT
			jedis.expire(lockName, lockTimeOutSecond);
		}
		return false;
	}

	public static boolean obtainLock(final UnifiedJedis jedis, final String lockName, final int lockTimeOutSecond) {
		Assertion.check().isNotNull(jedis)
				.isNotBlank(lockName)
				.isTrue(lockName.endsWith(".lock"), "Lock name, must ends with '.lock' ({0})", lockName);
		//----
		final String lock = jedis.set(lockName, String.valueOf(System.currentTimeMillis()), new SetParams().nx().ex(lockTimeOutSecond));
		return "OK".equals(lock); //check if we've got lock ( Redis.SET with NX params return null or "OK", @see https://redis.io/commands/set/ )
	}

	public static void releaseLock(final UnifiedJedis jedis, final String lockName) {
		Assertion.check().isNotNull(jedis)
				.isNotBlank(lockName)
				.isTrue(lockName.endsWith(".lock"), "Lock name, must ends with '.lock' ({0})", lockName);
		//----
		jedis.del(lockName);
	}
}
