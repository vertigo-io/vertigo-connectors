package io.vertigo.connectors.oidc;

import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines commons module.
 * @author skerdudou
 */
public class OIDCFeatures extends Features<OIDCFeatures> {

	/**
	 * Constructor.
	 */
	public OIDCFeatures() {
		super("vertigo-oidc-connector");
	}

	@Feature("oidc")
	public OIDCFeatures withConfig(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(OIDCDeploymentConnector.class, params);
		return this;

	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
