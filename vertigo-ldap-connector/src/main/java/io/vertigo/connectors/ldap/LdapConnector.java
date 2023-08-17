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
public class LdapConnector implements Connector<LdapContext> {
	private static final String DEFAULT_CONTEXT_FACTORY_CLASS_NAME = "com.sun.jndi.ldap.LdapCtxFactory";
	private static final String SIMPLE_AUTHENTICATION_MECHANISM_NAME = "simple";
	private static final String DEFAULT_REFERRAL = "follow";

	private final String connectorName;
	private final String ldapServer;
	private final Optional<String> readerLoginOpt;
	private final Optional<String> readerPasswordOpt;

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
			@ParamValue("readerLogin") final Optional<String> ldapReaderLoginOpt,
			@ParamValue("readerPassword") final Optional<String> ldapReaderPasswordOpt) {
		Assertion.check()
				.isNotBlank(ldapServerHost)
				.when(ldapReaderLoginOpt.isPresent(), () -> Assertion.check()
						.isFalse(StringUtil.isBlank(ldapReaderLoginOpt.get()), "readerLogin can't be empty")
						.isTrue(ldapReaderPasswordOpt.isPresent() && ldapReaderPasswordOpt.get() != null, "With readerLogin, readerPassword is mandatory"));
		//-----
		connectorName = connectorNameOpt.orElse("main");
		ldapServer = ldapServerHost + ":" + ldapServerPort;
		readerLoginOpt = ldapReaderLoginOpt;
		readerPasswordOpt = ldapReaderPasswordOpt;
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
			return createLdapContext(readerLoginOpt.get(), readerPasswordOpt.get());
		} catch (final NamingException e) {
			throw WrappedException.wrap(e, "Can't connect user : {0} ", readerLoginOpt);
		}
	}

}
