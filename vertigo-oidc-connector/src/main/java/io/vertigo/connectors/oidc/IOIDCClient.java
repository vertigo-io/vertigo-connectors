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

import java.io.Serializable;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

import io.vertigo.connectors.oidc.state.IOIDCStateStorage;

/**
 * Contract for an OpenID Connect client.
 *
 * @author skerdudou
 */
public interface IOIDCClient {

	/**
	 * Generates the login URL for the OIDC authentication request.
	 *
	 * @param callbackUri the callback URI to handle the authentication response
	 * @param oidcStateStorage the storage to keep the state and nonce for the callback
	 * @param localeOpt the optional locale to forward to the SSO
	 * @param additionalInfos additional infos retrievable at callback time
	 * @param requestedScopes the scopes requested for the authentication
	 * @return the URL to redirect the user to for OIDC authentication
	 */
	String getLoginUrl(URI callbackUri, IOIDCStateStorage oidcStateStorage, Optional<Locale> localeOpt, Map<String, Serializable> additionalInfos, String... requestedScopes);

	/**
	 * Get OIDC tokens from the SSO response.
	 *
	 * @param responseUri the current URI, with OIDC parameters (state and code)
	 * @param callbackUri the callback URI provided when sending user to SSO login page
	 * @param oidcStateStorage the storage to retrieve the state and nonce
	 * @return OIDC tokens (with ID token and Access token)
	 */
	OIDCTokens parseResponse(URI responseUri, URI callbackUri, IOIDCStateStorage oidcStateStorage);

	/**
	 * Retrieves the additional infos provided with the matching getLoginUrl call.
	 *
	 * @param responseUri the current URI, with OIDC parameters (state and code)
	 * @param oidcStateStorage the storage to retrieve the state
	 * @return the additional infos corresponding to those provided at login time
	 */
	Map<String, Serializable> retrieveAdditionalInfos(URI responseUri, IOIDCStateStorage oidcStateStorage);

	/**
	 * Build the logout URL for the SSO.
	 *
	 * @param redirectUriOpt the URL to redirect to after logout
	 * @param idTokenOpt the ID token of the connected user to logout
	 * @param localeOpt the user locale
	 * @return the URL to logout the user from the SSO
	 */
	String getLogoutUrl(Optional<URI> redirectUriOpt, Optional<String> idTokenOpt, Optional<Locale> localeOpt);
}
