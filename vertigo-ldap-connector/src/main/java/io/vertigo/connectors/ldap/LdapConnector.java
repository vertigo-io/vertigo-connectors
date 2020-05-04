/**
 * vertigo - simple java starter
 *
 * Copyright (C) 2013-2019, vertigo-io, KleeGroup, direction.technique@kleegroup.com (http://www.kleegroup.com)
 * KleeGroup, Centre d'affaire la Boursidiere - BP 159 - 92357 Le Plessis Robinson Cedex - France
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
package io.vertigo.connectors.ldap;

import java.util.Hashtable;
import java.util.Optional;

import javax.inject.Inject;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import io.vertigo.core.util.StringUtil;

/**
 * @author npiedeloup
 */
public final class LdapConnector implements Connector<LdapContext> {
	private static final String DEFAULT_CONTEXT_FACTORY_CLASS_NAME = "com.sun.jndi.ldap.LdapCtxFactory";
	private static final String SIMPLE_AUTHENTICATION_MECHANISM_NAME = "simple";
	private static final String DEFAULT_REFERRAL = "follow";

	private final String connectorName;
	private final String ldapServer;
	private final Optional<String> readerLogin;
	private final Optional<String> readerPassword;

	/**
	 * Constructor.
	 * @param ldapServerHost LDAP server host name
	 * @param ldapServerPort LDAP server port
	 */
	@Inject
	public LdapConnector(
			@ParamValue("name") final Optional<String> connectorNameOpt,
			@ParamValue("host") final String ldapServerHost,
			@ParamValue("port") final int ldapServerPort,
			@ParamValue("readerLogin") final Optional<String> ldapReaderLogin,
			@ParamValue("readerPassword") final Optional<String> ldapReaderPassword) {
		Assertion.checkArgNotEmpty(ldapServerHost);
		Assertion.when(ldapReaderLogin.isPresent())
				.state(() -> !StringUtil.isEmpty(ldapReaderLogin.get()), "readerLogin can't be empty")
				.state(() -> ldapReaderPassword.isPresent() && ldapReaderPassword.get() != null, "With readerLogin, readerPassword is mandatory");
		//-----
		connectorName = connectorNameOpt.orElse("main");
		ldapServer = ldapServerHost + ":" + ldapServerPort;
		readerLogin = ldapReaderLogin;
		readerPassword = ldapReaderPassword;
	}

	@Override
	public String getName() {
		return connectorName;
	}

	/**
	 * @return LDAP resource
	 */
	public LdapContext createLdapContext(final String userProtectedPrincipal, final String credentials) throws NamingException {
		final Hashtable<String, String> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, DEFAULT_CONTEXT_FACTORY_CLASS_NAME);
		env.put(Context.REFERRAL, DEFAULT_REFERRAL);

		env.put(Context.SECURITY_AUTHENTICATION, SIMPLE_AUTHENTICATION_MECHANISM_NAME);
		final String url = "ldap://" + ldapServer;
		env.put(Context.PROVIDER_URL, url);
		if (credentials != null) {
			env.put(Context.SECURITY_PRINCIPAL, userProtectedPrincipal);
			env.put(Context.SECURITY_CREDENTIALS, credentials);
		} else {
			env.put(Context.SECURITY_AUTHENTICATION, "none");
		}
		try {
			return new InitialLdapContext(env, null);
		} catch (final CommunicationException e) {
			throw WrappedException.wrap(e, "Can't connect to LDAP : {0} ", ldapServer);
		}
	}

	@Override
	public LdapContext getClient() {
		try {
			return createLdapContext(readerLogin.get(), readerPassword.get());
		} catch (final NamingException e) {
			throw WrappedException.wrap(e, "Can't connect user : {0} ", readerLogin);
		}
	}

}
