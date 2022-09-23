package io.vertigo.connectors.saml2;

import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigFilePlugin;
import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigKeystorePlugin;
import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigMetadataPlugin;
import io.vertigo.connectors.saml2.plugins.ip.SAML2IpConfigStringPlugin;
import io.vertigo.connectors.saml2.plugins.sp.SAML2SpKeyFilePlugin;
import io.vertigo.connectors.saml2.plugins.sp.SAML2SpKeyKeystorePlugin;
import io.vertigo.connectors.saml2.plugins.sp.SAML2SpKeyStringPlugin;
import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines SAML module.
 * @author skerdudou
 */
public class SAML2Features extends Features<SAML2Features> {

	/**
	 * Constructor.
	 */
	public SAML2Features() {
		super("vertigo-saml2-connector");
	}

	@Feature("saml")
	public SAML2Features withCommonConfig(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(SAML2DeploymentConnector.class, params);
		return this;
	}

	@Feature("saml.certif.keystore")
	public SAML2Features withKeystore(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2SpKeyKeystorePlugin.class, params);
		return this;
	}

	@Feature("saml.certif.file")
	public SAML2Features withKeyfiles(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2SpKeyFilePlugin.class, params);
		return this;
	}

	@Feature("saml.certif.string")
	public SAML2Features withStringKey(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2SpKeyStringPlugin.class, params);
		return this;
	}

	@Feature("saml.ip.string")
	public SAML2Features withIpStringKey(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2IpConfigStringPlugin.class, params);
		return this;
	}

	@Feature("saml.ip.file")
	public SAML2Features withIpFileKey(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2IpConfigFilePlugin.class, params);
		return this;
	}

	@Feature("saml.ip.keystore")
	public SAML2Features withIpKeystoreKey(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2IpConfigKeystorePlugin.class, params);
		return this;
	}

	@Feature("saml.ip.metadata")
	public SAML2Features withIpMetadataFile(final Param... params) {
		getModuleConfigBuilder()
				.addPlugin(SAML2IpConfigMetadataPlugin.class, params);
		return this;
	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
