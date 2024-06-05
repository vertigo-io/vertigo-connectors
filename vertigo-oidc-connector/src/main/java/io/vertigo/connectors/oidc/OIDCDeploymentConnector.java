/*
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
package io.vertigo.connectors.oidc;

import java.net.URL;
import java.util.Optional;

import javax.inject.Inject;

import io.vertigo.connectors.oidc.OIDCDeploymentConnector.OIDCParameters;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

/**
 * Component to configure OIDC authentication handler.
 *
 * @author skerdudou
 */
public class OIDCDeploymentConnector implements Connector<OIDCParameters> {

	private final OIDCParameters oidcParameters;
	private final String connectorName;

	@Inject
	public OIDCDeploymentConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("clientName") final String clientName,
			@ParamValue("clientSecret") final Optional<String> clientSecretOpt,
			@ParamValue("url") final String oidcUrl,
			@ParamValue("httpConnectTimeout") final Optional<Integer> httpConnectTimeoutOpt,
			@ParamValue("httpReadTimeout") final Optional<Integer> httpReadTimeoutOpt,
			@ParamValue("scopes") final Optional<String> requestedScopesOpt,
			@ParamValue("metadataFile") final Optional<String> localOIDCMetadataOpt,
			@ParamValue("jwsAlgorithm") final Optional<String> jwsAlgorithmOpt,
			@ParamValue("skipIdTokenValidation") final Optional<Boolean> skipIdTokenValidationOpt,
			@ParamValue("skipAutoconfigIssuerValidation") final Optional<Boolean> skipAutoconfigIssuerValidationOpt,
			@ParamValue("usePKCE") final Optional<Boolean> usePKCEOpt,
			@ParamValue("externalUrl") final Optional<String> externalUrlOpt,
			@ParamValue("dontFailAtStartup") final Optional<Boolean> dontFailAtStartupOpt,
			@ParamValue("trustStoreUrl") final Optional<String> trustStoreUrlOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt,
			@ParamValue("logoutRedirectUriParamName") final Optional<String> logoutRedirectUriParamNameOpt,
			@ParamValue("logoutIdParamName") final Optional<String> logoutIdParamNameOpt,
			@ParamValue("loginLocaleParamName") final Optional<String> loginLocaleParamNameOpt,
			final ResourceManager resourceManager) {

		Assertion.check()
				.isNotNull(connectorNameOpt)
				.isNotBlank(clientName)
				.isNotNull(clientSecretOpt)
				.isNotBlank(oidcUrl)
				.isNotNull(httpConnectTimeoutOpt)
				.isNotNull(httpReadTimeoutOpt)
				.isNotNull(localOIDCMetadataOpt)
				.isNotNull(jwsAlgorithmOpt)
				.isNotNull(skipIdTokenValidationOpt)
				.isNotNull(usePKCEOpt)
				.isNotNull(externalUrlOpt)
				.isNotNull(dontFailAtStartupOpt);
		//---
		connectorName = connectorNameOpt.orElse("main");
		oidcParameters = new OIDCParameters(
				clientName,
				clientSecretOpt,
				oidcUrl,
				httpConnectTimeoutOpt.orElseGet(() -> 1000),
				httpReadTimeoutOpt.orElseGet(() -> 1000),
				requestedScopesOpt.map(s -> s.split("\\s+")).orElse(new String[0]),
				localOIDCMetadataOpt.map(resourceManager::resolve),
				jwsAlgorithmOpt.orElse("RS256"),
				skipIdTokenValidationOpt.orElse(Boolean.FALSE),
				skipAutoconfigIssuerValidationOpt.orElse(Boolean.FALSE),
				usePKCEOpt.orElse(Boolean.TRUE),
				externalUrlOpt,
				dontFailAtStartupOpt.orElse(Boolean.FALSE),
				trustStoreUrlOpt, trustStorePasswordOpt,
				logoutRedirectUriParamNameOpt, logoutIdParamNameOpt, loginLocaleParamNameOpt);
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * Get config value object
	 *
	 * @return the config
	 */
	@Override
	public OIDCParameters getClient() {
		return oidcParameters;
	}

	public static final class OIDCParameters {
		private final String oidcClientName;
		private final Optional<String> oidcClientSecret;
		private final String oidcURL;
		private final int httpConnectTimeout; // milliseconds, used to fetch metadata from OIDC provider
		private final int httpReadTimeout; // milliseconds, used to fetch metadata from OIDC provider
		private final String[] requestedScopes;
		private final Optional<URL> localOIDCMetadataOp;
		private final String jwsAlgorithm;
		private final Boolean skipIdTokenValidation;
		private final Boolean skipAutoconfigIssuerValidation;
		private final Boolean usePKCE;

		private final Optional<String> logoutRedirectUriParamNameOpt;
		private final Optional<String> logoutIdParamNameOpt;
		private final Optional<String> loginLocaleParamNameOpt;

		private final Optional<String> externalUrlOpt;

		private final boolean dontFailAtStartup;

		private final Optional<String> trustStoreUrlOpt;
		private final Optional<String> trustStorePasswordOpt;

		public OIDCParameters(final String oidcClientName, final Optional<String> oidcClientSecret, final String oidcURL, final int httpConnectTimeout,
				final int httpReadTimeout, final String[] requestedScopes, final Optional<URL> localOIDCMetadataOp, final String jwsAlgorithm,
				final Boolean skipIdTokenValidation, final Boolean skipAutoconfigIssuerValidation, final Boolean usePKCE, final Optional<String> externalUrlOpt, final boolean dontFailAtStartup,
				final Optional<String> trustStoreUrlOpt, final Optional<String> trustStorePasswordOpt, final Optional<String> logoutRedirectUriParamNameOpt,
				final Optional<String> logoutIdParamNameOpt, final Optional<String> loginLocaleParamNameOpt) {

			this.oidcClientName = oidcClientName;
			this.oidcClientSecret = oidcClientSecret;
			this.oidcURL = oidcURL;
			this.httpConnectTimeout = httpConnectTimeout;
			this.httpReadTimeout = httpReadTimeout;
			this.requestedScopes = requestedScopes;
			this.localOIDCMetadataOp = localOIDCMetadataOp;
			this.jwsAlgorithm = jwsAlgorithm;
			this.skipIdTokenValidation = skipIdTokenValidation;
			this.skipAutoconfigIssuerValidation = skipAutoconfigIssuerValidation;
			this.usePKCE = usePKCE;
			this.externalUrlOpt = externalUrlOpt;
			this.dontFailAtStartup = dontFailAtStartup;
			this.trustStoreUrlOpt = trustStoreUrlOpt;
			this.trustStorePasswordOpt = trustStorePasswordOpt;
			this.logoutRedirectUriParamNameOpt = logoutRedirectUriParamNameOpt;
			this.logoutIdParamNameOpt = logoutIdParamNameOpt;
			this.loginLocaleParamNameOpt = loginLocaleParamNameOpt;
		}

		public final String getOidcClientName() {
			return oidcClientName;
		}

		public final Optional<String> getOidcClientSecret() {
			return oidcClientSecret;
		}

		public final String getOidcURL() {
			return oidcURL;
		}

		public final int getHttpConnectTimeout() {
			return httpConnectTimeout;
		}

		public final int getHttpReadTimeout() {
			return httpReadTimeout;
		}

		public final String[] getRequestedScopes() {
			return requestedScopes;
		}

		public final Optional<URL> getLocalOIDCMetadataOp() {
			return localOIDCMetadataOp;
		}

		public final String getJwsAlgorithm() {
			return jwsAlgorithm;
		}

		public final Boolean getSkipIdTokenValidation() {
			return skipIdTokenValidation;
		}

		public final Boolean getSkipAutoconfigIssuerValidation() {
			return skipAutoconfigIssuerValidation;
		}

		public final Boolean getUsePKCE() {
			return usePKCE;
		}

		public final Optional<String> getExternalUrlOpt() {
			return externalUrlOpt;
		}

		public final boolean isDontFailAtStartup() {
			return dontFailAtStartup;
		}

		public Optional<String> getTrustStoreUrlOpt() {
			return trustStoreUrlOpt;
		}

		public Optional<String> getTrustStorePasswordOpt() {
			return trustStorePasswordOpt;
		}

		public Optional<String> getLogoutRedirectUriParamNameOpt() {
			return logoutRedirectUriParamNameOpt;
		}

		public Optional<String> getLogoutIdParamNameOpt() {
			return logoutIdParamNameOpt;
		}

		public Optional<String> getLoginLocaleParamNameOpt() {
			return loginLocaleParamNameOpt;
		}

	}

}
