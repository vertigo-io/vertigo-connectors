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
package io.vertigo.connectors.azure.aad;

import java.net.MalformedURLException;
import java.util.Optional;

import javax.inject.Inject;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;

/**
 * Component to retrieve a configured ConfidentialClientApplication to connect to AzureAd.
 *
 * @author mlaroche
 *
 */
public class AzureAdConnector implements Connector<ConfidentialClientApplication> {

	private final String connectorName;

	private final String clientId;
	private final String clientSecret;
	private final String authorityUrl;

	@Inject
	public AzureAdConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clientId") final String clientId,
			@ParamValue("clientSecret") final String clientSecret,
			@ParamValue("authorityUrl") final String authorityUrl) {
		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clientId)
				.isNotBlank(clientSecret)
				.isNotBlank(authorityUrl);
		//---
		connectorName = connectorNameOpt.orElse("main");

		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.authorityUrl = authorityUrl;

	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * Get a ConfidentialClientApplication
	 * @return the ConfidentialClientApplication
	 */
	@Override
	public ConfidentialClientApplication getClient() {
		try {
			return ConfidentialClientApplication.builder(
					clientId,
					ClientCredentialFactory.createFromSecret(clientSecret))
					.authority(authorityUrl)
					.build();
		} catch (final MalformedURLException e) {
			throw WrappedException.wrap(e);
		}
	}

}
