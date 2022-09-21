package io.vertigo.connectors.jsch;

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

public class JSchConnectorFeatures extends Features<JSchConnectorFeatures> {

	public JSchConnectorFeatures() {
		super("vertigo-jsch-connector");
	}

	@Feature("jsch.keyAuth")
	public JSchConnectorFeatures withKeyAuthentication(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(JSchKeyAuthConnector.class, params);
		return this;

	}

	@Override
	protected void buildFeatures() {
		getModuleConfigBuilder().build();

	}

}
