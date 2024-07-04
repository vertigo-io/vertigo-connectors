/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2024, Vertigo.io, team@vertigo.io
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

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines commons module.
 * @author pchretien
 */
public final class RedisFeatures extends Features<RedisFeatures> {

	/**
	 * Constructor.
	 */
	public RedisFeatures() {
		super("vertigo-redis-connector");
	}

	@Feature("jedis-single")
	public RedisFeatures withJedisSingle(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(RedisSingleConnector.class, params);
		return this;

	}

	@Feature("jedis")
	public RedisFeatures withJedis(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(RedisConnector.class, params);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
