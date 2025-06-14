/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2025, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.oidc;

import java.util.Optional;

import javax.inject.Inject;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * Component to configure OIDC authentication handler.
 *
 * @author skerdudou
 */
public class OIDCDeploymentConnector implements Connector<OIDCClient> {

	private final OIDCClient oidcClient;
	private final String connectorName;

	@Inject
	public OIDCDeploymentConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clientName") final String clientName,
			@ParamValue("clientSecret") final Optional<String> clientSecretOpt,
			@ParamValue("url") final String oidcUrl,
			@ParamValue("overrideIssuer") final Optional<String> overrideIssuerOpt,
			@ParamValue("httpConnectTimeout") final Optional<Integer> httpConnectTimeoutOpt,
			@ParamValue("httpReadTimeout") final Optional<Integer> httpReadTimeoutOpt,
			@ParamValue("metadataFile") final Optional<String> localOIDCMetadataOpt,
			@ParamValue("jwsAlgorithm") final Optional<String> jwsAlgorithmOpt,
			@ParamValue("skipIdTokenValidation") final Optional<Boolean> skipIdTokenValidationOpt,
			@ParamValue("usePKCE") final Optional<Boolean> usePKCEOpt,
			@ParamValue("dontFailAtStartup") final Optional<Boolean> dontFailAtStartupOpt,
			@ParamValue("trustStoreUrl") final Optional<String> trustStoreUrlOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt,
			@ParamValue("logoutRedirectUriParamName") final Optional<String> logoutRedirectUriParamNameOpt,
			@ParamValue("logoutIdParamName") final Optional<String> logoutIdParamNameOpt,
			@ParamValue("localeParamName") final Optional<String> localeParamNameOpt,
			final ResourceManager resourceManager) {

		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clientName)
				.isNotNull(clientSecretOpt)
				.isNotBlank(oidcUrl)
				.isNotNull(overrideIssuerOpt)
				.isNotNull(httpConnectTimeoutOpt)
				.isNotNull(httpReadTimeoutOpt)
				.isNotNull(localOIDCMetadataOpt)
				.isNotNull(jwsAlgorithmOpt)
				.isNotNull(skipIdTokenValidationOpt)
				.isNotNull(usePKCEOpt)
				.isNotNull(dontFailAtStartupOpt)
				.isNotNull(trustStoreUrlOpt)
				.isNotNull(trustStorePasswordOpt)
				.isNotNull(logoutRedirectUriParamNameOpt)
				.isNotNull(logoutIdParamNameOpt)
				.isNotNull(localeParamNameOpt);
		//---
		connectorName = connectorNameOpt.orElse("main");

		final OIDCParameters oidcParameters = new OIDCParameters(
				clientName,
				clientSecretOpt,
				oidcUrl,
				overrideIssuerOpt,
				httpConnectTimeoutOpt.orElse(1000),
				httpReadTimeoutOpt.orElse(1000),
				localOIDCMetadataOpt.map(resourceManager::resolve),
				jwsAlgorithmOpt.orElse("RS256"),
				skipIdTokenValidationOpt.orElse(Boolean.FALSE),
				usePKCEOpt.orElse(Boolean.TRUE),
				logoutRedirectUriParamNameOpt,
				logoutIdParamNameOpt,
				localeParamNameOpt,
				dontFailAtStartupOpt.orElse(Boolean.FALSE),
				trustStoreUrlOpt,
				trustStorePasswordOpt);

		oidcClient = new OIDCClient(oidcParameters);
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * Get custom client over NimbusDS OIDC client.
	 *
	 * @return the config
	 */
	@Override
	public OIDCClient getClient() {
		return oidcClient;
	}

}
