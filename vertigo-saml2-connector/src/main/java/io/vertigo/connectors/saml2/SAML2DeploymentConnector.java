/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2026, Vertigo.io, team@vertigo.io
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

	private final SAML2Parameters samlParameters;
	private final String connectorName;
	/** Beyond, the validity times of the assertion are no longer really checked. */
	private static final int MAX_CLOCK_SKEW_SECONDS = 600;

	@Inject
	public SAML2DeploymentConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clientName") final String clientName,
			@ParamValue("externalUrl") final Optional<String> externalUrlOpt,
			@ParamValue("signatureType") final Optional<String> signatureTypeOpt,
			@ParamValue("cryptAssertion") final Optional<Boolean> cryptAssertionOpt,
			@ParamValue("extractPublicKeyFromCertificate") final Optional<Boolean> extractPublicKeyFromCertificateOpt,
			@ParamValue("clockSkewSeconds") final Optional<Integer> clockSkewSecondsOpt,
			@ParamValue("checkAudience") final Optional<Boolean> checkAudienceOpt,
			@ParamValue("checkDestination") final Optional<Boolean> checkDestinationOpt,
			@ParamValue("checkInResponseTo") final Optional<Boolean> checkInResponseToOpt,
			final SAML2SpKeyPlugin spKeyConfig,
			final SAML2IpConfigPlugin ipConfig,
			final ResourceManager resourceManager) {

		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clientName)
				.isNotNull(externalUrlOpt)
				.isNotNull(signatureTypeOpt)
				.isNotNull(cryptAssertionOpt)
				.isNotNull(clockSkewSecondsOpt)
				.isNotNull(checkAudienceOpt)
				.isNotNull(checkDestinationOpt)
				.isNotNull(checkInResponseToOpt)
				.isNotNull(spKeyConfig)
				.isNotNull(ipConfig)
				.isNotNull(resourceManager)
				.isTrue(clockSkewSecondsOpt.orElse(0) >= 0 && clockSkewSecondsOpt.orElse(0) <= MAX_CLOCK_SKEW_SECONDS,
						"clockSkewSeconds (clock gap accepted between the IdP and the application) must be between 0 and {0}s : {1}s", MAX_CLOCK_SKEW_SECONDS, clockSkewSecondsOpt.orElse(0))
				.when(externalUrlOpt.filter(url -> !url.isBlank()).isPresent(), () -> Assertion.check()
						.isTrue(externalUrlOpt.get().matches("(?i)https?://[^/].*") && !externalUrlOpt.get().endsWith("/"),
								"externalUrl must be the absolute url of the application, without final / (the callback path is added to it) : {0}", externalUrlOpt.get()));
		//---
		connectorName = connectorNameOpt.orElse("main");
		samlParameters = new SAML2Parameters(
				clientName,
				externalUrlOpt.filter(url -> !url.isBlank()), //an empty value (an empty environment variable) is no externalUrl
				signatureTypeOpt.orElse("RSA-SHA256"),
				cryptAssertionOpt.orElse(true),
				extractPublicKeyFromCertificateOpt.orElse(false),
				// checks of the SAML 2.0 Web Browser SSO profile, on by default ; InResponseTo off : it needs the session at the callback (cross site POST from the IdP)
				clockSkewSecondsOpt.orElse(180),
				checkAudienceOpt.orElse(true),
				checkDestinationOpt.orElse(true),
				checkInResponseToOpt.orElse(false),
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
		return samlParameters;
	}

}
