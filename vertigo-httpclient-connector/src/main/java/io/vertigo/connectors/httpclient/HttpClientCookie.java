package io.vertigo.connectors.httpclient;

import java.net.CookieManager;
import java.util.Optional;

public final class HttpClientCookie implements AutoCloseable {

	private static final ThreadLocal<CookieManager> THREAD_LOCAL_COOKIE = new ThreadLocal<>();

	public HttpClientCookie() {
		THREAD_LOCAL_COOKIE.set(new CookieManager());
	}

	public static Optional<CookieManager> getCurrentCookieManager() {
		return Optional.ofNullable(THREAD_LOCAL_COOKIE.get());
	}

	@Override
	public void close() {
		THREAD_LOCAL_COOKIE.remove();
	}

}
