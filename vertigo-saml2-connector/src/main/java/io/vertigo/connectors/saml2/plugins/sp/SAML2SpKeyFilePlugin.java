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
