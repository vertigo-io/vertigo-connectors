package io.vertigo.connectors.oidc;

import java.net.URL;
import java.util.Optional;

public final class OIDCParameters {
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

	OIDCParameters(final String oidcClientName,
			final Optional<String> oidcClientSecret,
			final String oidcURL,
			final int httpConnectTimeout,
			final int httpReadTimeout,
			final String[] requestedScopes,
			final Optional<URL> localOIDCMetadataOp,
			final String jwsAlgorithm,
			final Boolean skipIdTokenValidation,
			final Boolean skipAutoconfigIssuerValidation,
			final Boolean usePKCE,
			final Optional<String> externalUrlOpt,
			final boolean dontFailAtStartup,
			final Optional<String> trustStoreUrlOpt,
			final Optional<String> trustStorePasswordOpt,
			final Optional<String> logoutRedirectUriParamNameOpt,
			final Optional<String> logoutIdParamNameOpt,
			final Optional<String> loginLocaleParamNameOpt) {

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
