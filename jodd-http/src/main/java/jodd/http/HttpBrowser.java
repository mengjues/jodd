// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.http;

import jodd.util.StringPool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Emulates HTTP Browser and persist cookies between requests.
 */
public class HttpBrowser {

	protected HttpConnectionProvider httpConnectionProvider;
	protected HttpRequest httpRequest;
	protected HttpResponse httpResponse;
	protected Map<String, Cookie> cookies = new LinkedHashMap<String, Cookie>();
	protected HttpValuesMap<String> defaultHeaders = HttpValuesMap.ofStrings();
	protected boolean keepAlive;
	protected long elapsedTime;

	public HttpBrowser() {
		httpConnectionProvider = JoddHttp.httpConnectionProvider;
	}

	/**
	 * Returns <code>true</code> if keep alive is used.
	 */
	public boolean isKeepAlive() {
		return keepAlive;
	}

	/**
	 * Defines that persistent HTTP connection should be used.
	 */
	public HttpBrowser setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	/**
	 * Defines proxy for a browser.
	 */
	public HttpBrowser setProxyInfo(ProxyInfo proxyInfo) {
		httpConnectionProvider.useProxy(proxyInfo);
		return this;
	}

	/**
	 * Defines {@link jodd.http.HttpConnectionProvider} for this browser session.
	 * Resets the previous proxy definition, if set.
	 */
	public HttpBrowser setHttpConnectionProvider(HttpConnectionProvider httpConnectionProvider) {
		this.httpConnectionProvider = httpConnectionProvider;
		return this;
	}

	/**
	 * Adds default header to all requests.
	 */
	public HttpBrowser setDefaultHeader(String name, String value) {
		defaultHeaders.add(name, value);
		return this;
	}

	/**
	 * Returns last used request.
	 */
	public HttpRequest getHttpRequest() {
		return httpRequest;
	}

	/**
	 * Returns last received {@link HttpResponse HTTP response} object.
	 */
	public HttpResponse getHttpResponse() {
		return httpResponse;
	}

	/**
	 * Returns last response HTML page.
	 */
	public String getPage() {
		if (httpResponse == null) {
			return null;
		}
		return httpResponse.bodyText();
	}


	/**
	 * Sends new request as a browser. Before sending,
	 * all browser cookies are added to the request.
	 * After sending, the cookies are read from the response.
	 * Moreover, status codes 301 and 302 are automatically
	 * handled. Returns very last response.
	 */
	public HttpResponse sendRequest(HttpRequest httpRequest) {
		elapsedTime = System.currentTimeMillis();

		// send request

		while (true) {
			this.httpRequest = httpRequest;
			HttpResponse previouseResponse = this.httpResponse;
			this.httpResponse = null;

			addDefaultHeaders(httpRequest);
			addCookies(httpRequest);

			// send request
			if (keepAlive == false) {
				httpRequest.open(httpConnectionProvider);
			} else {
				// keeping alive
				if (previouseResponse == null) {
					httpRequest.open(httpConnectionProvider).connectionKeepAlive(true);
				} else {
					httpRequest.keepAlive(previouseResponse, true);
				}
			}

			this.httpResponse = httpRequest.send();

			readCookies(httpResponse);

			int statusCode = httpResponse.statusCode();

			// 301: moved permanently
			if (statusCode == 301) {
				String newPath = location(httpResponse);

				httpRequest = HttpRequest.get(newPath);
				continue;
			}

			// 302: redirect, 303: see other
			if (statusCode == 302 || statusCode == 303) {
				String newPath = location(httpResponse);

				httpRequest = HttpRequest.get(newPath);
				continue;
			}

			// 307: temporary redirect
			if (statusCode == 307) {
				String newPath = location(httpResponse);

				String originalMethod = httpRequest.method();
				httpRequest = new HttpRequest()
						.method(originalMethod)
						.set(newPath);
				continue;
			}

			break;
		}

		elapsedTime = System.currentTimeMillis() - elapsedTime;

		return this.httpResponse;
	}

	/**
	 * Add default headers to the request. If request already has a header set,
	 * default header will be ignored.
	 */
	protected void addDefaultHeaders(HttpRequest httpRequest) {
		Set<Map.Entry<String, String[]>> entries = defaultHeaders.entrySet();

		for (Map.Entry<String, String[]> entry : entries) {
			String name = entry.getKey();
			if (!httpRequest.headers.containsKey(name)) {
				httpRequest.headers.put(name, entry.getValue());
			}
		}
	}

	/**
	 * Parse 'location' header to return the next location.
	 * Specification (<a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.30">rfc2616</a>)
	 * says that only absolute path must be provided, however, this does not
	 * happens in the real world. There a <a href="https://tools.ietf.org/html/rfc7231#section-7.1.2">proposal</a>
	 * that allows server name etc to be omitted.
	 */
	protected String location(HttpResponse httpResponse) {
		String location = httpResponse.header("location");

		if (location.startsWith(StringPool.SLASH)) {
			HttpRequest httpRequest = httpResponse.getHttpRequest();
			location = httpRequest.hostUrl() + location;
		}

		return location;
	}

	/**
	 * Returns elapsed time of last {@link #sendRequest(HttpRequest)} in milliseconds.
	 */
	public long getElapsedTime() {
		return elapsedTime;
	}

	// ---------------------------------------------------------------- close

	/**
	 * Closes browser explicitly, needed when keep-alive connection is used.
	 */
	public void close() {
		if (httpResponse != null) {
			httpResponse.close();
		}
	}

	// ---------------------------------------------------------------- cookies

	/**
	 * Reads cookies from response.
	 */
	protected void readCookies(HttpResponse httpResponse) {
		String[] newCookies = httpResponse.headers("set-cookie");

		if (newCookies != null) {
			for (String cookieValue : newCookies) {
				Cookie cookie = new Cookie(cookieValue);
				cookies.put(cookie.getName(), cookie);
			}
		}
	}

	/**
	 * Add cookies to the request.
	 */
	protected void addCookies(HttpRequest httpRequest) {
		// prepare all cookies

		StringBuilder cookieString = new StringBuilder();
		boolean first = true;

		if (!cookies.isEmpty()) {
			for (Cookie cookie: cookies.values()) {
                
			    Integer maxAge = cookie.getMaxAge();
				if (maxAge != null && maxAge.intValue() == 0) {
				    continue;
				}

				if (!first) {
					cookieString.append("; ");
				}
				first = false;
				cookieString.append(cookie.getName());
				cookieString.append('=');
				cookieString.append(cookie.getValue());
			}

			httpRequest.header("cookie", cookieString.toString(), true);
		}
	}
}
