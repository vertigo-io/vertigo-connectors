/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2025, Vertigo.io, team@vertigo.io
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
package io.vertigo.connectors.oidc.state;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.util.StringUtil;
import jakarta.servlet.http.HttpSession;

/**
 * Store OIDC states in session.
 */
public final class OIDCSessionStateStorage implements IOIDCStateStorage {

	private static final Logger LOG = LogManager.getLogger(OIDCSessionStateStorage.class);

	private static final String STATES = "states";
	private static final Integer STATE_TTL = 3600;
	private static final String OIDC_ID_TOKEN = "OIDC_ID_TOKEN";

	private final HttpSession session;

	public static OIDCSessionStateStorage of(final HttpSession session) {
		return new OIDCSessionStateStorage(session);
	}

	private OIDCSessionStateStorage(final HttpSession session) {
		this.session = session;
	}

	@Override
	public OIDCStateData retrieveStateDataFromSession(final String state) {
		if (LOG.isTraceEnabled()) {
			LOG.trace("Retrieving state '{}' from session '{}'", state, session.getId());
		}
		if (!StringUtil.isBlank(state)) {
			final var stateDataInSession = removeStateFromSession(state);
			if (stateDataInSession != null) {
				return stateDataInSession;
			}
		}
		throw new VSystemException("Failed to validate data received from Authorization service - could not validate state");
	}

	@Override
	public Map<String, Serializable> retrieveAdditionalInfos(final String state) {
		if (LOG.isTraceEnabled()) {
			LOG.trace("Retrieving additional infos for state '{}' from session '{}'", state, session.getId());
			dumpStates();
		}
		if (!StringUtil.isBlank(state)) {
			final var states = (Map<String, OIDCStateData>) session.getAttribute(STATES);
			if (states != null) {
				final var stateData = states.get(state);
				if (stateData != null) {
					return stateData.additionalInfos();
				}
			}
		}
		// in OIDCWebAuthenticationPlugin, we use this map to store previously requestedUri to redirect to after auth
		// the "no state" can happen if we go back after auth, keycloak will redirect with the previous state that does not exists anymore (we have deleted if on login success)
		// we still not permit to authenticate with a previously used state but we do not want to stop redirect on already authenticated session, so we dont throw an error here
		return Map.of();
	}

	private void dumpStates() {
		final var states = (Map<String, OIDCStateData>) session.getAttribute(STATES);
		if (states != null) {
			LOG.trace("Existing states in session '{}'", session.getId());
			for (final Map.Entry<String, OIDCStateData> entry : states.entrySet()) {
				LOG.trace("\tState: '{}' Data: '{}'", entry.getKey(), entry.getValue());
			}
		} else {
			LOG.trace("No states in session '{}'", session.getId());
		}
	}

	private OIDCStateData removeStateFromSession(final String state) {
		if (LOG.isTraceEnabled()) {
			LOG.trace("Removing state '{}' from session '{}'", state, session.getId());
		}
		final var states = (Map<String, OIDCStateData>) session.getAttribute(STATES);
		if (states != null) {
			eliminateExpiredStates(states);
			final var stateData = states.get(state);
			if (stateData != null) {
				states.remove(state);
				if (LOG.isTraceEnabled()) {
					LOG.trace("Removing state '{}' from session '{}'", state, session.getId());
				}
				session.setAttribute(STATES, states); //needed for correct cluster sync (see fb-contrib:SCSS_SUSPICIOUS_CLUSTERED_SESSION_SUPPORT)
				return stateData;
			}
		}
		return null;
	}

	private void eliminateExpiredStates(final Map<String, OIDCStateData> map) {
		final var it = map.entrySet().iterator();

		final var currTime = new Date();
		while (it.hasNext()) {
			final var entry = it.next();
			final var diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(currTime.getTime() - entry.getValue().stateDate().getTime());

			if (diffInSeconds > STATE_TTL) {
				if (LOG.isTraceEnabled()) {
					LOG.trace("Removing expired state '{}'", entry.getKey());
				}
				it.remove();
			}
		}
	}

	@Override
	public void storeStateDataInSession(final String state, final String nonce, final String pkceCodeVerifier, final Map<String, Serializable> additionalInfos) {
		// state parameter to validate response from Authorization server and nonce parameter to validate idToken
		final var states = Optional.ofNullable((Map<String, OIDCStateData>) session.getAttribute(STATES))
				.orElseGet(HashMap::new);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Storing state '{}' in session '{}'", state, session.getId());
		}
		states.put(state, new OIDCStateData(nonce, pkceCodeVerifier, new Date(), additionalInfos));
		session.setAttribute(STATES, states);
	}

	public static void storeIdTokenInSession(final HttpSession session, final String idToken) {
		session.setAttribute(OIDC_ID_TOKEN, idToken);
	}

	public static String retrieveIdTokenFromSession(final HttpSession session) {
		return (String) session.getAttribute(OIDC_ID_TOKEN);
	}

}
