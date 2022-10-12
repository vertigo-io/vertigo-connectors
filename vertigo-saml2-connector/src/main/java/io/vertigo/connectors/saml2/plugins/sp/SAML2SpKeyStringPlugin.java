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
package io.vertigo.connectors.saml2.plugins.sp;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opensaml.security.credential.Credential;

import io.vertigo.connectors.saml2.SAML2SpKeyPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.util.StringUtil;

public class SAML2SpKeyStringPlugin implements SAML2SpKeyPlugin {

	private final List<Credential> credentials;

	@Inject
	public SAML2SpKeyStringPlugin(
			@ParamValue("publicKey") final String myPublicKey,
			@ParamValue("privateKey") final String myPrivateKey,
			@ParamValue("nextPublicKey") final Optional<String> nextPublicKeyOpt,
			@ParamValue("nextPrivateKey") final Optional<String> nextPrivateKeyOpt) {

		Security.addProvider(new BouncyCastleProvider()); //PKCS1 support

		credentials = new ArrayList<>();
		credentials.add(CertUtil.getCredentialFromString(myPublicKey, myPrivateKey));

		if (nextPublicKeyOpt.isPresent() != nextPrivateKeyOpt.isPresent()
				|| (nextPublicKeyOpt.isPresent() && StringUtil.isBlank(nextPublicKeyOpt.get()) != StringUtil.isBlank(nextPrivateKeyOpt.get()))) {
			throw new VSystemException("publicKey2 and privateKey2 must be defined accordingly.");
		}

		if (nextPublicKeyOpt.isPresent()) {
			credentials.add(CertUtil.getCredentialFromString(nextPublicKeyOpt.get(), nextPrivateKeyOpt.get()));
		}
	}

	@Override
	public List<Credential> getCredentials() {
		return credentials;
	}
}
