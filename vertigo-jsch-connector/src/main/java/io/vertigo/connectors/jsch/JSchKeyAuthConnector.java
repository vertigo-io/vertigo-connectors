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
package io.vertigo.connectors.jsch;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Optional;

import javax.inject.Inject;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.jcraft.jsch.JSch;

import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;

public class JSchKeyAuthConnector implements JSchConnector {

	private JSch jSch;

	@Inject
	public JSchKeyAuthConnector(
			final ResourceManager resourceManager,
			@ParamValue("username") final String username,
			@ParamValue("keyStoreUrl") final String keyStoreUrl,
			@ParamValue("keyStorePassword") final String keyStorePassword,
			@ParamValue("privateKeyAlias") final String privateKeyAlias,
			@ParamValue("knownHostUrlOpt") final Optional<String> knownHostUrlOpt) {
		try {
			final var createdJSch = new JSch();
			final var keyStoreResolvedUrl = resourceManager.resolve(keyStoreUrl);
			final var jks = KeyStore.getInstance("PKCS12");
			jks.load(keyStoreResolvedUrl.openStream(), keyStorePassword.toCharArray());
			final var privateKey = (PrivateKey) jks.getKey(privateKeyAlias, keyStorePassword.toCharArray());
			final var stringWriter = new StringWriter();
			final var pemWriter = new JcaPEMWriter(stringWriter);
			pemWriter.writeObject(privateKey);
			pemWriter.close();
			final var privateKeyPEM = stringWriter.toString().getBytes(StandardCharsets.UTF_8);

			createdJSch.addIdentity(username, privateKeyPEM, null, null);

			if (knownHostUrlOpt.isPresent()) {
				createdJSch.setKnownHosts(knownHostUrlOpt.get());
			}
			this.jSch = createdJSch;
		} catch (final Exception e) {
			throw WrappedException.wrap(e);
		}

	}

	@Override
	public JSch getClient() {
		return jSch;
	}

}
