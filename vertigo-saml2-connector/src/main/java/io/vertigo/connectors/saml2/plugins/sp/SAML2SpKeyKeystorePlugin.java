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
