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
package io.vertigo.connectors.javalin;

import java.io.IOException;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.MimeTypes;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;

/**
 * Filter to configure MultipartConfigElement for Jetty Request.
 * @author npiedeloup
 */
public final class JettyMultipartCleaner implements Handler {
	private static final Logger LOG = LogManager.getLogger(JettyMultipartCleaner.class);

	/** {@inheritDoc} */
	@Override
	public void handle(final Context ctx) {
		try {
			final String contentType = ctx.req().getContentType();
			if (contentType != null && MimeTypes.Type.MULTIPART_FORM_DATA.is(HttpField.valueParameters(contentType, null))) {
				final Collection<Part> multiParts = ctx.req().getParts();
				if (multiParts != null && !multiParts.isEmpty()) {
					for (final Part part : multiParts) {
						try {
							// a multipart request to a servlet will have the parts cleaned up correctly, but
							// the repeated call to deleteParts() here will safely do nothing.
							part.delete();
						} catch (final IOException e) {
							LOG.warn("Error while deleting multipart request parts", e);
						}
					}
				}
			}
		} catch (IOException | ServletException errorParts) {
			LOG.warn("Error while deleting multipart request parts", errorParts);
		}
	}
}
