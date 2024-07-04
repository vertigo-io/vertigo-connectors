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
package io.vertigo.connectors.saml2;

import java.util.Optional;

import javax.inject.Inject;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * Component to configure SAML2 authentication handler.
 *
 * @author skerdudou
 *
 */
public class SAML2DeploymentConnector implements Connector<SAML2Parameters> {

	private final SAML2Parameters oidcParameters;
	private final String connectorName;

	@Inject
	public SAML2DeploymentConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clientName") final String clientName,
			@ParamValue("externalUrl") final Optional<String> externalUrlOpt,
			@ParamValue("signatureType") final Optional<String> signatureTypeOpt,
			@ParamValue("cryptAssertion") final Optional<Boolean> cryptAssertionOpt,
			@ParamValue("extractPublicKeyFromCertificate") final Optional<Boolean> extractPublicKeyFromCertificateOpt,
			final SAML2SpKeyPlugin spKeyConfig,
			final SAML2IpConfigPlugin ipConfig,
			final ResourceManager resourceManager) {

		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clientName)
				.isNotNull(externalUrlOpt)
				.isNotNull(signatureTypeOpt)
				.isNotNull(cryptAssertionOpt)
				.isNotNull(spKeyConfig)
				.isNotNull(ipConfig)
				.isNotNull(resourceManager);
		//---
		connectorName = connectorNameOpt.orElse("main");
		oidcParameters = new SAML2Parameters(
				clientName,
				externalUrlOpt,
				signatureTypeOpt.orElse("RSA-SAH256"),
				cryptAssertionOpt.orElse(true),
				extractPublicKeyFromCertificateOpt.orElse(false),
				spKeyConfig.getCredentials(),
				ipConfig.getLoginUrl(),
				ipConfig.getLogoutUrl(),
				ipConfig.getPublicCredentials());
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * Get config value object
	 * @return the config
	 */
	@Override
	public SAML2Parameters getClient() {
		return oidcParameters;
	}

}
