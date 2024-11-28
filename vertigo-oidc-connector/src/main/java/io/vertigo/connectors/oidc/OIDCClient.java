package io.vertigo.connectors.oidc;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import javax.inject.Inject;
import javax.net.ssl.SSLSocketFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.AuthorizationSuccessResponse;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPRequestConfigurator;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.util.tls.TLSUtils;
import com.nimbusds.oauth2.sdk.util.tls.TLSVersion;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;

import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.node.Node;
import io.vertigo.core.node.component.di.DIInjector;
import io.vertigo.core.resource.ResourceManager;
import io.vertigo.core.util.StringUtil;
import jakarta.servlet.http.HttpSession;
import net.minidev.json.JSONObject;

public class OIDCClient {

	private static final String OIDC_ID_TOKEN = "OIDC_ID_TOKEN";
	private static final Logger LOG = LogManager.getLogger(OIDCClient.class);
	// if metadata is not available at startup, limit check frequency at runtime
	private static final int MIN_TIME_BETWEEN_METATADA_CHECK = 60;

	private final OIDCParameters oidcParameters;

	private final ClientID clientID;
	private OIDCProviderMetadata ssoMetadata;
	private IDTokenValidator idTokenValidator;

	private Instant lastMetadataCheck;
	private final Optional<SSLSocketFactory> sslSocketFactoryOpt;

	@Inject
	private ResourceManager resourceManager;

	public OIDCClient(final OIDCParameters oidcParameters) {
		DIInjector.injectMembers(this, Node.getNode().getComponentSpace());

		this.oidcParameters = oidcParameters;

		clientID = new ClientID(oidcParameters.oidcClientName());

		if (oidcParameters.trustStoreUrlOpt().isPresent()) {
			// load custom trust store
			try {
				sslSocketFactoryOpt = Optional.of(createSSLSocketFactory(
						resourceManager.resolve(oidcParameters.trustStoreUrlOpt().get()),
						oidcParameters.trustStorePasswordOpt()));
			} catch (final Exception e) {
				throw WrappedException.wrap(e);
			}
		} else {
			sslSocketFactoryOpt = Optional.empty();
		}

		loadMetadataIfNeeded(oidcParameters.dontFailAtStartup());
	}

	private static SSLSocketFactory createSSLSocketFactory(final URL trustStoreUrl, final Optional<String> trustStorePassword) throws GeneralSecurityException, IOException {
		final var trustStore = KeyStore.getInstance("pkcs12");
		try (var inputStream = trustStoreUrl.openStream()) {
			trustStore.load(inputStream, trustStorePassword.map(String::toCharArray).orElseGet(() -> null));
		}

		return TLSUtils.createSSLSocketFactory(trustStore, TLSVersion.TLS_1_3);
	}

	private synchronized void loadMetadataIfNeeded(final boolean silentFail) {
		if (ssoMetadata != null) {
			return;
		}
		if (lastMetadataCheck == null || Instant.now().getEpochSecond() > lastMetadataCheck.getEpochSecond() + MIN_TIME_BETWEEN_METATADA_CHECK) {
			lastMetadataCheck = Instant.now();

			if (silentFail) {
				try {
					doLoadMetadata();
				} catch (final RuntimeException e) {
					LOG.warn("Unable to load OIDC metadata, login temporarily disabled.", e);
				}
			} else {
				doLoadMetadata();
			}
		} else {
			LOG.info("OIDC metadata not loaded, wait before next try.");
			throw new VSystemException("Sorry, authentification is currently unavaiable.");
		}
	}

	private void doLoadMetadata() {
		// get OIDC Metadata from file if provided or from the provider itself
		final var localOIDCMetadataOp = oidcParameters.localOIDCMetadataOp();
		if (localOIDCMetadataOp.isPresent()) {
			ssoMetadata = getOidcMetadataFromFile(localOIDCMetadataOp.get());
		}

		final var issuer = new Issuer(oidcParameters.overrideIssuerOpt().orElse(oidcParameters.oidcURL()));
		if (ssoMetadata == null) { // no file or error reading file
			ssoMetadata = getOidcMetadataFromRemote(issuer, oidcParameters.httpConnectTimeout(), oidcParameters.httpReadTimeout());
		}

		final var jwsAlgorithm = JWSAlgorithm.parse(oidcParameters.jwsAlgorithm().toUpperCase());
		if (Family.HMAC_SHA.contains(jwsAlgorithm)) {
			final var paddedKey = new String(getPaddedSecretKeyBytes(), StandardCharsets.UTF_8);
			idTokenValidator = new IDTokenValidator(issuer, clientID, jwsAlgorithm, new Secret(paddedKey));
		} else {
			final var resourceRetriever = new DefaultResourceRetriever(oidcParameters.httpConnectTimeout(), oidcParameters.httpReadTimeout(), 0, true, sslSocketFactoryOpt.orElse(null));

			try {
				idTokenValidator = new IDTokenValidator(issuer, clientID, jwsAlgorithm, ssoMetadata.getJWKSetURI().toURL(), resourceRetriever);
			} catch (final MalformedURLException e) {
				throw WrappedException.wrap(e);
			}
		}
	}

	private byte[] getPaddedSecretKeyBytes() {
		// hmac validator need at least 32 bytes for the key or else throw an exception (we can pad data with 0s)
		final var oidcClientSecretOpt = oidcParameters.oidcClientSecret();
		if (oidcClientSecretOpt.isEmpty()) {
			throw new VSystemException("HMAC type jwsAlgorithm needs clientSecret.");
		}
		final var secretKey = oidcClientSecretOpt.get().getBytes(StandardCharsets.UTF_8);
		if (secretKey.length < 32) {
			LOG.warn("OIDC secret key is {} bytes long, recommanded to be at least 32 bytes (256 bits).", secretKey.length);
			return Arrays.copyOf(secretKey, 32);
		}
		return secretKey;
	}

	private static OIDCProviderMetadata getOidcMetadataFromFile(final URL url) {
		try (var stream = url.openStream()) {
			return OIDCProviderMetadata.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		} catch (ParseException | IOException e) {
			LOG.warn(String.format("Unable to read OIDC metadata from provided file '%s'", url), e);
			return null;
		}
	}

	private OIDCProviderMetadata getOidcMetadataFromRemote(final Issuer issuer, final int httpConnectTimeout, final int httpReadTimeout) {
		try {

			final HTTPRequestConfigurator requestConfigurator = new HTTPRequestConfigurator() {

				@Override
				public void configure(final HTTPRequest httpRequest) {
					httpRequest.setConnectTimeout(httpConnectTimeout);
					httpRequest.setReadTimeout(httpReadTimeout);
					if (sslSocketFactoryOpt.isPresent()) {
						httpRequest.setSSLSocketFactory(sslSocketFactoryOpt.get());
					}
				}
			};
			if (oidcParameters.overrideIssuerOpt().isPresent()) {
				return resolveAlternateIssuerValidation(new Issuer(oidcParameters.oidcURL()), issuer, requestConfigurator);
			}
			return OIDCProviderMetadata.resolve(issuer, requestConfigurator);
		} catch (GeneralException | IOException e) {
			throw new VSystemException(e, "Can't read remote OpenId metadata at '{0}'", issuer.getValue());
		}
	}

	/**
	 * Fork of OIDCProviderMetadata.resolve to change issuer validation.
	 * Keycloak, if called by internal URL, still return the issuer with his external URL.
	 */
	private static OIDCProviderMetadata resolveAlternateIssuerValidation(
			final Issuer issuer,
			final Issuer returnedIssuer,
			final HTTPRequestConfigurator requestConfigurator)
			throws GeneralException, IOException {

		final URL configURL = OIDCProviderMetadata.resolveURL(issuer);

		final HTTPRequest httpRequest = new HTTPRequest(HTTPRequest.Method.GET, configURL);
		requestConfigurator.configure(httpRequest);

		final HTTPResponse httpResponse = httpRequest.send();

		if (httpResponse.getStatusCode() != 200) {
			throw new IOException("Couldn't download OpenID Provider metadata from " + configURL +
					": Status code " + httpResponse.getStatusCode());
		}

		final JSONObject jsonObject = httpResponse.getContentAsJSONObject();

		final OIDCProviderMetadata op = OIDCProviderMetadata.parse(jsonObject);

		if (!returnedIssuer.equals(op.getIssuer())) {
			throw new GeneralException("The returned issuer doesn't match the expected: " + op.getIssuer());
		}

		return op;
	}

	/**
	 * Generates the login URL for the OpenID Connect (OIDC) authentication request.
	 *
	 * @param redirectUri the URI to redirect to after authentication
	 * @param callbackUri the callback URI to handle the authentication response
	 * @param session the current HTTP session
	 * @param localeOpt the optional locale to forward to the SSO. Sent if localeParamName is configured.
	 * @param requestedScopes the scopes requested for the authentication
	 * @return the URL to redirect the user to for OIDC authentication
	 */
	public String getLoginUrl(final URI redirectUri, final URI callbackUri, final HttpSession session, final Optional<Locale> localeOpt, final String... requestedScopes) {
		loadMetadataIfNeeded(false);

		// Generate random state string to securely pair the callback to this request and a corresponding nonce
		// save all this in http session paired with the original requested URL to forward user after authentication
		final var state = new State();
		final var nonce = new Nonce();
		final Scope scope = new Scope(requestedScopes);
		scope.add("openid"); // mandatory scope

		final var codeVerifier = Boolean.TRUE.equals(oidcParameters.usePKCE()) ? new CodeVerifier() : null;
		OIDCSessionManagementUtil.storeStateDataInSession(session, state.getValue(), nonce.getValue(), codeVerifier == null ? null : codeVerifier.getValue(), redirectUri.toString());

		// Compose the OpenID authentication request (for the code flow)
		final var authRequestBuilder = new AuthenticationRequest.Builder(
				ResponseType.CODE,
				scope,
				clientID,
				callbackUri)
						.endpointURI(ssoMetadata.getAuthorizationEndpointURI())
						.state(state)
						.nonce(nonce)
						.codeChallenge(codeVerifier, CodeChallengeMethod.S256);

		// forward user locale to the SSO, for example keycloak uses ui_locales parameter
		if (oidcParameters.localeParamNameOpt().isPresent()) {
			authRequestBuilder.customParameter(oidcParameters.localeParamNameOpt().get(), localeOpt.orElse(Locale.FRENCH).getLanguage());
		}

		final var authRequest = authRequestBuilder.build();

		return authRequest.toURI().toString();
	}

	/**
	 * Get OIDC tokens from the SSO response.
	 *
	 * @param responseUri the current URI, with OIDC parameters (state and code)
	 * @param callbackUri the callback URI provided when sending user to SSO login page
	 * @param session the current session
	 * @return OIDC tokens (with ID token and Access token).
	 */
	public OIDCTokens parseResponse(final URI responseUri, final URI callbackUri, final HttpSession session) {
		final var successResponse = parseResponseUri(responseUri);

		final var state = successResponse.getState();
		final var stateData = OIDCSessionManagementUtil.retrieveStateDataFromSession(session, state.getValue());
		loadMetadataIfNeeded(false);

		final var oidcTokens = doGetOIDCTokens(successResponse.getAuthorizationCode(), stateData.pkceCodeVerifier(), callbackUri);

		if (!Boolean.TRUE.equals(oidcParameters.skipIdTokenValidation())) {
			doValidateToken(oidcTokens.getIDToken(), stateData.nonce());
		}

		session.setAttribute(OIDC_ID_TOKEN, oidcTokens.getIDTokenString()); // store ID token in session, keycloak needs it for logout with redirect

		return oidcTokens;
	}

	private static AuthorizationSuccessResponse parseResponseUri(final URI responseUri) {
		final AuthenticationResponse authResponse;
		try {
			authResponse = AuthenticationResponseParser.parse(responseUri);
		} catch (final com.nimbusds.oauth2.sdk.ParseException e) {
			throw new VSystemException(e, "Error while parsing callback URL");
		}

		if (!authResponse.indicatesSuccess()) {
			// The request was denied or some error occurred
			final var errorObject = authResponse.toErrorResponse().getErrorObject();
			throw new VSystemException("Invalid OIDC response '{0} : {1}'", errorObject.getCode(), errorObject.getDescription());
		}

		return authResponse.toSuccessResponse();
	}

	private OIDCTokens doGetOIDCTokens(final AuthorizationCode code, final String pkceCodeVerifier, final URI callbackURI) {
		// The token endpoint
		final var tokenEndpoint = ssoMetadata.getTokenEndpointURI();

		final AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callbackURI, pkceCodeVerifier == null ? null : new CodeVerifier(pkceCodeVerifier));

		// Make the token request
		TokenRequest request;
		final var optSecret = oidcParameters.oidcClientSecret();
		if (optSecret.isEmpty() || StringUtil.isBlank(optSecret.get())) {
			request = new TokenRequest(tokenEndpoint, clientID, codeGrant);
		} else {
			final var clientSecret = new Secret(optSecret.get());
			final ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);
			request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant);
		}

		// Call the endpoint
		final TokenResponse tokenResponse;
		try {
			final HTTPRequest httpRequest = request.toHTTPRequest();
			if (sslSocketFactoryOpt.isPresent()) {
				httpRequest.setSSLSocketFactory(sslSocketFactoryOpt.get());
			}
			tokenResponse = OIDCTokenResponseParser.parse(httpRequest.send());
		} catch (com.nimbusds.oauth2.sdk.ParseException | IOException e) {
			throw new VSystemException(e, "Unable to retreive token from OIDC provider");
		}

		if (!tokenResponse.indicatesSuccess()) {
			// We got an error response...
			final var errorObject = tokenResponse.toErrorResponse().getErrorObject();
			throw new VSystemException("Invalid OIDC token response '{0} : {1}'", errorObject.getCode(), errorObject.getDescription());
		}

		return ((OIDCTokenResponse) tokenResponse.toSuccessResponse()).getOIDCTokens();
	}

	private void doValidateToken(final JWT idToken, final String expectedNonce) {
		try {
			final var claims = idTokenValidator.validate(idToken, new Nonce(expectedNonce));
			LOG.info("Valid OIDC Id token received for user '{}'.", claims.getSubject());
		} catch (BadJOSEException | JOSEException e) {
			throw new VSystemException(e, "Error validating OIDC Id token.");
		}
	}

	/**
	 * Build the logout URL for the SSO.
	 *
	 * @param redirectUriOpt the URL to redirect to after logout
	 * @param sessionOpt the user session if any, needed for logoutIdParamName to be sent (prevent session ending confirmation by the SSO)
	 * @param localeOpt the user locale, default to French. Sent if localeParamName is configured.
	 * @return the URL to logout the user from the SSO
	 */
	public String getLogoutUrl(final Optional<URI> redirectUriOpt, final Optional<HttpSession> sessionOpt, final Optional<Locale> localeOpt) {
		String logoutParam = "?client_id=" + clientID.getValue();

		if (redirectUriOpt.isPresent()) {
			if (oidcParameters.logoutRedirectUriParamNameOpt().isPresent()) {
				final var redirectUrl = redirectUriOpt.get().toString();
				logoutParam += "&" + oidcParameters.logoutRedirectUriParamNameOpt().get() + "=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);
			}
		}
		if (oidcParameters.logoutIdParamNameOpt().isPresent() && sessionOpt.isPresent()) {
			final String idToken = (String) sessionOpt.get().getAttribute(OIDC_ID_TOKEN);
			if (!StringUtil.isBlank(idToken)) { //if we have a OIDC ID TOKEN : we send it to the logout endpoint
				logoutParam += "&";
				logoutParam += oidcParameters.logoutIdParamNameOpt().get() + "=" + idToken;
			}
		}

		// forward user locale to the SSO, for example keycloak uses ui_locales parameter
		if (oidcParameters.localeParamNameOpt().isPresent()) {
			logoutParam += "&";
			logoutParam += oidcParameters.localeParamNameOpt().get() + "=" + localeOpt.orElse(Locale.FRENCH).getLanguage();
		}

		return ssoMetadata.getEndSessionEndpointURI().toString() + logoutParam;
	}

	/**
	 * Retrieves the originally requested URI from the session using the state parameter from the OIDC response.
	 *
	 * @param responseUri the current URI, with OIDC parameters (state and code)
	 * @param session the current HTTP session
	 * @return the originally requested URI, if available
	 */
	public Optional<String> getRequestedUri(final URI responseUri, final HttpSession session) {
		final var successResponse = parseResponseUri(responseUri);
		final var state = successResponse.getState();
		return Optional.ofNullable(OIDCSessionManagementUtil.getRequestedUri(session, state.getValue()));
	}
}
