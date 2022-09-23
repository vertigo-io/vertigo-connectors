package io.vertigo.connectors.saml2.plugins.ip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;

import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
import io.vertigo.core.util.StringUtil;

public class SAML2IpConfigFilePlugin extends SAML2IpConfigStringPlugin {

	@Inject
	public SAML2IpConfigFilePlugin(
			@ParamValue("loginUrl") final String loginUrl,
			@ParamValue("logoutUrl") final String logoutUrl,
			@ParamValue("publicKeyFile") final String publicKey,
			@ParamValue("publicKey2File") final Optional<String> publicKey2Opt,
			final ResourceManager resourceManager) {

		super(loginUrl, logoutUrl,
				readFileContent(resourceManager, publicKey),
				publicKey2Opt
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
