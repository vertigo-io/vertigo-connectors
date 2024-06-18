package io.vertigo.connectors.s3;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.minio.MinioClient;
import io.minio.http.HttpUtils;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.node.component.Connector;
import io.vertigo.core.param.ParamValue;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Protocol;

/**
 * Minio (S3) client provider.
 *
 * @author skerdudou
 */
public class S3Connector implements Connector<MinioClient> {
	private static final Logger LOG = LogManager.getLogger(S3Connector.class);

	private static final long DEFAULT_CONNECTION_TIMEOUT = 30;

	private final MinioClient minioClient;

	@Inject
	public S3Connector(
			@ParamValue("endpointURL") final String endpointURL,
			@ParamValue("accessKey") final String accessKey,
			@ParamValue("secretKey") final String secretKey,
			@ParamValue("connectionTimeoutSeconds") final Optional<Long> connectionTimeoutOpt,
			@ParamValue("skipHostnameCheck") final Optional<Boolean> skipHostnameCheckOpt,
			@ParamValue("publicCert") final Optional<String> publicCertOpt,
			@ParamValue("trustStore") final Optional<String> trustStoreOpt,
			@ParamValue("trustStorePassword") final Optional<String> trustStorePasswordOpt) {

		Assertion.check()
				.isNotBlank(endpointURL)
				.isNotBlank(accessKey)
				.isNotBlank(secretKey)
				.isNotNull(connectionTimeoutOpt)
				.isNotNull(skipHostnameCheckOpt)
				.isNotNull(publicCertOpt)
				.isNotNull(trustStoreOpt)
				.isNotNull(trustStorePasswordOpt)
				.isTrue(publicCertOpt.isEmpty() || trustStoreOpt.isEmpty(), "Cannot configure both publicCert and trustStore.");
		//-----

		final long connectionTimeout = connectionTimeoutOpt.orElse(DEFAULT_CONNECTION_TIMEOUT);
		final boolean skipHostnameCheck = skipHostnameCheckOpt.orElse(Boolean.FALSE);

		if (skipHostnameCheck) {
			LOG.warn("Hostname verification on S3 connector disabled. Not safe for production.");
		}

		if (publicCertOpt.isPresent()) {
			// minio public certificate
			OkHttpClient okHttpClient;
			try {
				okHttpClient = HttpUtils.enableExternalCertificates(getOkHttpDefaultBuilder(connectionTimeout, skipHostnameCheck).build(), publicCertOpt.get());
			} catch (GeneralSecurityException | IOException e) {
				throw new VSystemException(e, "Unable to load truststore file '{0}'.", trustStoreOpt.get());
			}
			minioClient = MinioClient.builder()
					.endpoint(endpointURL)
					.credentials(accessKey, secretKey)
					.httpClient(okHttpClient)
					.build();
		} else if (trustStoreOpt.isPresent()) {
			// custom truststore
			final OkHttpClient okHttpClient = buildSslOkHttpClient(
					trustStoreOpt.get(),
					trustStorePasswordOpt.map(String::toCharArray).orElse(null),
					connectionTimeout,
					skipHostnameCheck);

			minioClient = MinioClient.builder()
					.endpoint(endpointURL)
					.credentials(accessKey, secretKey)
					.httpClient(okHttpClient)
					.build();
		} else {
			// no ssl or jvm truststore
			minioClient = MinioClient.builder()
					.endpoint(endpointURL)
					.credentials(accessKey, secretKey)
					.httpClient(getOkHttpDefaultBuilder(connectionTimeout, skipHostnameCheck).build()) // overwrite default timeout of 5 minutes
					.build();
		}
	}

	private static OkHttpClient buildSslOkHttpClient(final String trustStorePath, final char[] keyStorePassword, final long connectionTimeout, final boolean skipHostnameCheck) {
		try {
			final var keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(new URL("file:" + trustStorePath).openStream(), keyStorePassword);

			final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keyStore, keyStorePassword);
			final KeyManager[] keyManagers = kmf.getKeyManagers();

			final TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
			tmf.init(keyStore);
			final TrustManager[] trustManagers = tmf.getTrustManagers();

			final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(keyManagers, trustManagers, new SecureRandom());
			return getOkHttpDefaultBuilder(connectionTimeout, skipHostnameCheck)
					.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
					.build();
		} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException | UnrecoverableKeyException e) {
			throw new VSystemException(e, "Unable to load truststore file '{0}'.", trustStorePath);
		}
	}

	private static Builder getOkHttpDefaultBuilder(final long connectionTimeout, final boolean skipHostnameCheck) {
		// inspired from default minIo client (cf io.minio.http.HttpUtils.newDefaultHttpClient)
		final Builder builder = new OkHttpClient.Builder()
				.connectTimeout(connectionTimeout, TimeUnit.SECONDS)
				.writeTimeout(connectionTimeout, TimeUnit.SECONDS)
				.readTimeout(connectionTimeout, TimeUnit.SECONDS)
				.protocols(Arrays.asList(Protocol.HTTP_1_1));
		if (skipHostnameCheck) {
			builder.hostnameVerifier((a, b) -> true);
		}
		return builder;
	}

	@Override
	public MinioClient getClient() {
		return minioClient;
	}

}
