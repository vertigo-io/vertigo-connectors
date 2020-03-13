/**
 * vertigo - simple java starter
 *
 * Copyright (C) 2013-2019, vertigo-io, KleeGroup, direction.technique@kleegroup.com (http://www.kleegroup.com)
 * KleeGroup, Centre d'affaire la Boursidiere - BP 159 - 92357 Le Plessis Robinson Cedex - France
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
package io.vertigo.connectors.ifttt;

import java.util.Optional;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * @author dt
 */
public final class IftttConnector implements Connector<IftttClient> {
	private final String connectorName;

	private final IftttClient iftttClient;

	public IftttConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("baseUrl") final String baseUrl,
			@ParamValue("apiKey") final String apiKey,
			@ParamValue("proxyHost") final Optional<String> proxyHostOpt,
			@ParamValue("proxyPort") final Optional<String> proxyPortOpt) {
		Assertion.checkNotNull(connectorNameOpt);
		//---
		this.connectorName = connectorNameOpt.orElse("main");
		iftttClient = new IftttClient(baseUrl, apiKey, proxyHostOpt, proxyPortOpt);
	}

	@Override
	public String getName() {
		return connectorName;
	}

	@Override
	public IftttClient getClient() {
		return iftttClient;
	}
}
