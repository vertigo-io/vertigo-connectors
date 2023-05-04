/**
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
package io.vertigo.connectors.saml2.plugins.ip;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opensaml.security.credential.Credential;

import io.vertigo.connectors.saml2.SAML2IpConfigPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.util.StringUtil;

public class SAML2IpConfigStringPlugin implements SAML2IpConfigPlugin {

	private final String loginUrl;
	private final String logoutUrl;
	private final List<Credential> credentials;

	@Inject
	public SAML2IpConfigStringPlugin(
			@ParamValue("loginUrl") final String loginUrl,
			@ParamValue("logoutUrl") final String logoutUrl,
			@ParamValue("publicKey") final String publicKey,
			@ParamValue("nextPublicKey") final Optional<String> nextPublicKeyOpt) {

		Security.addProvider(new BouncyCastleProvider()); //PKCS1 support

		credentials = new ArrayList<>();

		this.loginUrl = loginUrl;
		this.logoutUrl = logoutUrl;

		credentials.add(CertUtil.getCredentialFromString(publicKey));
		if (nextPublicKeyOpt.isPresent() && !StringUtil.isBlank(nextPublicKeyOpt.get())) {
			credentials.add(CertUtil.getCredentialFromString(nextPublicKeyOpt.get()));
		}

	}

	@Override
	public String getLoginUrl() {
		return loginUrl;
	}

	@Override
	public String getLogoutUrl() {
		return logoutUrl;
	}

	@Override
	public List<Credential> getPublicCredentials() {
		return credentials;
	}

}
