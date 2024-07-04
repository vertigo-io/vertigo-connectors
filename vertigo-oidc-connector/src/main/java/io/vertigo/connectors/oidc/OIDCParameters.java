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
