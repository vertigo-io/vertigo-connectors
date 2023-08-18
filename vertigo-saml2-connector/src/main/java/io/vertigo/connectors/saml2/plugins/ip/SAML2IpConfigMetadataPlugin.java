/*
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
package io.vertigo.connectors.saml2.plugins.ip;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.criterion.ProtocolCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractBatchMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.FilesystemMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.criteria.UsageCriterion;
import org.opensaml.xmlsec.config.impl.DefaultSecurityConfigurationBootstrap;

import io.vertigo.connectors.saml2.OpenSAMLUtil;
import io.vertigo.connectors.saml2.SAML2IpConfigPlugin;
import io.vertigo.connectors.saml2.plugins.CertUtil;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.resource.ResourceManager;
import io.vertigo.core.util.StringUtil;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.ParserPool;

public class SAML2IpConfigMetadataPlugin implements SAML2IpConfigPlugin {

	private final String loginUrl;
	private final String logoutUrl;
	private final List<Credential> signingCredentials;

	@Inject
	public SAML2IpConfigMetadataPlugin(
			@ParamValue("metadataFile") final String metadataFilePath,
			@ParamValue("nextMetadataFile") final Optional<String> nextMetadataFileOpt,
			@ParamValue("nextCertFile") final Optional<String> alternateCertFileOpt,
			@ParamValue("simpleLogoutUrl") final Optional<String> simpleLogoutUrl,
			final ResourceManager resourceManager) {

		final var parserPool = OpenSAMLUtil.initOpenSamlIfNeeded();

		final var metadataFileUrl = resourceManager.resolve(metadataFilePath);
		final var metadataResolver = getMetadataResolver(metadataFileUrl, parserPool);

		final var idpEntityDescriptor = getEntityDescriptor(metadataResolver);

		loginUrl = resolveLoginUrl(idpEntityDescriptor);
		logoutUrl = simpleLogoutUrl.orElseGet(() -> resolveLogoutUrl(idpEntityDescriptor));

		signingCredentials = new ArrayList<>();
		signingCredentials.add(resolveIdpCredential(metadataResolver, idpEntityDescriptor));

		if (nextMetadataFileOpt.isPresent() && !StringUtil.isBlank(nextMetadataFileOpt.get())) {
			final var metadataFile2Url = resourceManager.resolve(nextMetadataFileOpt.get());
			final var metadataResolver2 = getMetadataResolver(metadataFile2Url, parserPool);
			final var idpEntityDescriptor2 = getEntityDescriptor(metadataResolver);
			signingCredentials.add(resolveIdpCredential(metadataResolver2, idpEntityDescriptor2));
		}

		if (alternateCertFileOpt.isPresent() && !StringUtil.isBlank(alternateCertFileOpt.get())) {
			signingCredentials.add(CertUtil.getCredentialFromString(alternateCertFileOpt.get()));
		}
	}

	private static AbstractReloadingMetadataResolver getMetadataResolver(final URL metadataFileUrl, final ParserPool parserPool) {
		try {
			final AbstractReloadingMetadataResolver metadataResolver;
			if ("file".equals(metadataFileUrl.getProtocol())) {
				final var f = new File(metadataFileUrl.getPath());
				metadataResolver = new FilesystemMetadataResolver(f);
			} else {
				final HttpClient client = HttpClients.createDefault();
				metadataResolver = new HTTPMetadataResolver(client, metadataFileUrl.toExternalForm());
			}
			metadataResolver.setId(metadataResolver.getClass().getCanonicalName());
			metadataResolver.setParserPool(parserPool);
			metadataResolver.initialize();
			return metadataResolver;
		} catch (final ResolverException | ComponentInitializationException e) {
			throw WrappedException.wrap(e);
		}
	}

	private static Credential resolveIdpCredential(final AbstractReloadingMetadataResolver metadataResolver, final EntityDescriptor idpEntityDescriptor) {
		try {
			final var metadataCredentialResolver = new MetadataCredentialResolver();

			final var roleResolver = new PredicateRoleDescriptorResolver(metadataResolver);
			roleResolver.initialize();
			metadataCredentialResolver.setRoleDescriptorResolver(roleResolver);

			final var keyResolver = DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver();
			metadataCredentialResolver.setKeyInfoCredentialResolver(keyResolver);

			metadataCredentialResolver.initialize();

			final var criteriaSetSigning = getCredentialCriteriaSet(idpEntityDescriptor.getEntityID(), UsageType.SIGNING);
			return metadataCredentialResolver.resolveSingle(criteriaSetSigning);
			// Saml IDP metadata provides encryption public key but I don't found an usage, it can be retrieved with getCredentialCriteriaSet(..., UsageType.ENCRYPTION)
		} catch (ComponentInitializationException | ResolverException e) {
			throw new VSystemException(e, "Unable to resolve credentials from SAML metadata.");

		}
	}

	private static CriteriaSet getCredentialCriteriaSet(final String entityId, final UsageType usageType) {
		final var criteriaSet = new CriteriaSet();
		criteriaSet.add(new UsageCriterion(usageType));
		criteriaSet.add(new EntityRoleCriterion(SPSSODescriptor.DEFAULT_ELEMENT_NAME));
		criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
		criteriaSet.add(new EntityIdCriterion(entityId));

		return criteriaSet;
	}

	private static EntityDescriptor getEntityDescriptor(final AbstractBatchMetadataResolver metadataResolver) {
		final var it = metadataResolver.iterator();
		if (!it.hasNext()) {
			throw new VSystemException("Unable to read SAML metadata file.");
		}
		return it.next();
	}

	private static String resolveLoginUrl(final EntityDescriptor idpEntityDescriptor) {
		return resolveUrl(idpEntityDescriptor, IDPSSODescriptor::getSingleSignOnServices);
	}

	private static String resolveLogoutUrl(final EntityDescriptor idpEntityDescriptor) {
		return resolveUrl(idpEntityDescriptor, IDPSSODescriptor::getSingleLogoutServices);
	}

	private static String resolveUrl(final EntityDescriptor idpEntityDescriptor, final Function<IDPSSODescriptor, List<? extends Endpoint>> endpointAccessor) {
		Endpoint redirectEndpoint = null;
		final var idpssoDescriptor = idpEntityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
		for (final Endpoint ep : endpointAccessor.apply(idpssoDescriptor)) {
			if (ep.getBinding().equals(SAMLConstants.SAML2_POST_BINDING_URI)) {
				redirectEndpoint = ep;
				break; // stop if we got one that match
			}
		}

		if (redirectEndpoint == null) {
			throw new VSystemException("Unable to resolve URL (HTTP-POST) from SAML metadata.");
		}

		return redirectEndpoint.getLocation();
	}

	@Override
	public String getLoginUrl() {
		return loginUrl;
	}

	@Override
	public String getLogoutUrl() {
		return logoutUrl;
	}

	@Override
	public List<Credential> getPublicCredentials() {
		return signingCredentials;
	}
}
