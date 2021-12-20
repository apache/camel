/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Describe a resource, such as a file or class path resource.
 */
public interface Resource {

    /**
     * The scheme of the resource such as file, classpath, http
     */
    String getScheme();

    /**
     * The location of the resource.
     */
    String getLocation();

    /**
     * Whether this resource exists.
     */
    boolean exists();

    /**
     * The {@link URI} of the resource.
     * </p>
     * The default implementation creates a {@code URI} object from resource location.
     */
    default URI getURI() {
        return URI.create(getLocation());
    }

    /**
     * The {@link URL} for the resource or <code>null</code> if the URL can not be computed.
     * </p>
     * The default implementation creates a {@code URI} object from resource location.
     */
    default URL getURL() throws MalformedURLException {
        URI uri = getURI();
        return uri != null ? uri.toURL() : null;
    }

    /**
     * Returns an {@link InputStream} that reads from the underlying resource.
     * </p>
     * Each invocation must return a new {@link InputStream} instance.
     */
    InputStream getInputStream() throws IOException;

    /**
     * Returns a {@link Reader} that reads from the underlying resource using UTF-8 as charset.
     * </p>
     * Each invocation must return a new {@link Reader}.
     *
     * @see #getInputStream()
     */
    default Reader getReader() throws Exception {
        return getReader(StandardCharsets.UTF_8);
    }

    /**
     * Returns a {@link Reader} that reads from the underlying resource using the given {@link Charset}
     * </p>
     * Each invocation must return a new {@link Reader}.
     *
     * @see #getInputStream()
     */
    default Reader getReader(Charset charset) throws Exception {
        return new InputStreamReader(getInputStream(), charset);
    }
}
