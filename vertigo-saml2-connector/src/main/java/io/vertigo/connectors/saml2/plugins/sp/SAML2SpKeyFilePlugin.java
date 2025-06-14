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
package io.vertigo.connectors.saml2.plugins.sp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;

import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
import io.vertigo.core.util.StringUtil;

public class SAML2SpKeyFilePlugin extends SAML2SpKeyStringPlugin {

	@Inject
	public SAML2SpKeyFilePlugin(
			@ParamValue("myPublicKeyFile") final String myPublicKey,
			@ParamValue("myPrivateKeyFile") final String myPrivateKey,
			@ParamValue("myNextPublicKeyFile") final Optional<String> myNextPublicKeyFileOpt,
			@ParamValue("myNextPrivateKeyFile") final Optional<String> myNextPrivateKeyFileOpt,
			final ResourceManager resourceManager) {

		super(readFileContent(resourceManager, myPublicKey),
				readFileContent(resourceManager, myPrivateKey),
				myNextPublicKeyFileOpt
						.filter(Predicate.not(StringUtil::isBlank))
						.map(keyFile -> readFileContent(resourceManager, keyFile)),
				myNextPrivateKeyFileOpt
						.filter(Predicate.not(StringUtil::isBlank))
						.map(keyFile -> readFileContent(resourceManager, keyFile)));
	}

	private static String readFileContent(final ResourceManager resourceManager, final String path) {
		final var fileUrl = resourceManager.resolve(path);
		try (var stream = fileUrl.openStream()) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw WrappedException.wrap(e);
		}
	}
}
