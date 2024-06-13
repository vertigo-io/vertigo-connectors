package io.vertigo.connectors.oidc;

import java.net.URL;
import java.util.Optional;

public final record OIDCParameters(
		String oidcClientName,
		Optional<String> oidcClientSecret,
		String oidcURL,
		Optional<String> overrideIssuerOpt,
		int httpConnectTimeout, // milliseconds, used to fetch metadata from OIDC provider
		int httpReadTimeout, // milliseconds, used to fetch metadata from OIDC provider
		String[] requestedScopes,
		Optional<URL> localOIDCMetadataOp,
		String jwsAlgorithm,
		Boolean skipIdTokenValidation,
		Boolean usePKCE,

		Optional<String> logoutRedirectUriParamNameOpt,
		Optional<String> logoutIdParamNameOpt,
		Optional<String> loginLocaleParamNameOpt,

		Optional<String> externalUrlOpt,

		boolean dontFailAtStartup,

		Optional<String> trustStoreUrlOpt,
		Optional<String> trustStorePasswordOpt) {
}
