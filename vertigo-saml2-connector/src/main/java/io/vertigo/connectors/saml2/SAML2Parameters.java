/**
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2022, Vertigo.io, team@vertigo.io
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

import java.util.List;
import java.util.Optional;

import org.opensaml.security.credential.Credential;

public final class SAML2Parameters {
	private final String samlClientName;

	private final Optional<String> externalUrlOpt;

	private final String signatureType;
	private final boolean cryptAssertion;

	private final boolean extractPublicKeyFromCertificate;

	private final List<Credential> spCredentials;
	private final String loginUrl;
	private final String logoutUrl;
	private final List<Credential> ipPublicCredentials;

	public SAML2Parameters(
			final String samlClientName,
			final Optional<String> externalUrlOpt,
			final String signatureType,
			final boolean cryptAssertion,
			final boolean extractPublicKeyFromCertificate,
			final List<Credential> spCredentials,
			final String loginUrl,
			final String logoutUrl,
			final List<Credential> ipPublicCredentials) {

		this.samlClientName = samlClientName;
		this.externalUrlOpt = externalUrlOpt;
		this.signatureType = signatureType;
		this.cryptAssertion = cryptAssertion;
		this.extractPublicKeyFromCertificate = extractPublicKeyFromCertificate;
		this.spCredentials = spCredentials;
		this.loginUrl = loginUrl;
		this.logoutUrl = logoutUrl;
		this.ipPublicCredentials = ipPublicCredentials;
	}

	public String getSamlClientName() {
		return samlClientName;
	}

	public Optional<String> getExternalUrlOpt() {
		return externalUrlOpt;
	}

	public String getSignatureType() {
		return signatureType;
	}

	public boolean isCryptAssertion() {
		return cryptAssertion;
	}

	public boolean isExtractPublicKeyFromCertificate() {
		return extractPublicKeyFromCertificate;
	}

	public List<Credential> getSpCredentials() {
		return spCredentials;
	}

	public Credential getSpCredential() {
		return spCredentials.get(0);
	}

	public String getLoginUrl() {
		return loginUrl;
	}

	public String getLogoutUrl() {
		return logoutUrl;
	}

	public List<Credential> getIpPublicCredentials() {
		return ipPublicCredentials;
	}

}
