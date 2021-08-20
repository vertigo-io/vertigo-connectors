package io.vertigo.connectors.azure;

import io.vertigo.connectors.azure.aad.AzureAdConnector;
import io.vertigo.core.node.config.Feature;
import io.vertigo.core.node.config.Features;
import io.vertigo.core.param.Param;

/**
 * Defines Azure module.
 * @author mlaroche
 */
public final class AzureFeatures extends Features<AzureFeatures> {

	/**
	 * Constructor.
	 */
	public AzureFeatures() {
		super("vertigo-azure-connector");
	}

	@Feature("aad")
	public AzureFeatures withAzureAd(final Param... params) {
		getModuleConfigBuilder()
				.addConnector(AzureAdConnector.class, params);
		return this;

	}

	/** {@inheritDoc} */
	@Override
	protected void buildFeatures() {
		//
	}
}
