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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.opensaml.security.credential.Credential;

import io.vertigo.connectors.saml2.SAML2SpKeyPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class SAML2SpKeyKeystorePlugin implements SAML2SpKeyPlugin {

	private final List<Credential> credential;

	@Inject
	public SAML2SpKeyKeystorePlugin(
			@ParamValue("file") final String keystoreFile,
			@ParamValue("password") final String keystorePassword,
			@ParamValue("aliases") final String aliases,
			@ParamValue("keystoreType") final Optional<String> keystoreTypeOpt,
			final ResourceManager resourceManager) {

		credential = CertUtil.getCredentialsFromKeystore(
				resourceManager.resolve(keystoreFile),
				aliases.split(";"),
				keystorePassword.toCharArray(),
				true,
				keystoreTypeOpt.orElse("pkcs12"));
	}

	@Override
	public List<Credential> getCredentials() {
		return credential;
	}
}
