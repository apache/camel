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
package org.apache.camel.impl.engine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContentTypeAware;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ResourceResolver;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ResourceResolverSupport;
import org.apache.camel.support.ResourceSupport;
import org.apache.camel.util.FileUtil;

public final class DefaultResourceResolvers {

    /**
     * Property key for how long to wait for the connection to an {@code http:} or {@code https:} resource to be
     * established, in milliseconds. {@code 0} means wait indefinitely.
     */
    public static final String HTTP_CONNECT_TIMEOUT_PROPERTY = "camel.resource.http.connect-timeout";

    /**
     * Property key for how long to wait for data when reading an {@code http:} or {@code https:} resource, in
     * milliseconds. {@code 0} means wait indefinitely.
     */
    public static final String HTTP_READ_TIMEOUT_PROPERTY = "camel.resource.http.read-timeout";

    static final int DEFAULT_HTTP_CONNECT_TIMEOUT = 10000;
    static final int DEFAULT_HTTP_READ_TIMEOUT = 30000;

    private DefaultResourceResolvers() {
    }

    /**
     * Resolves a timeout from the properties component, falling back to the given default.
     * <p/>
     * Resolution happens per resource rather than once per resolver because a resolver is a long-lived service while
     * the properties it reads can be reloaded underneath it.
     */
    private static int resolveTimeout(CamelContext camelContext, String key, int defaultValue) {
        if (camelContext == null) {
            return defaultValue;
        }
        return camelContext.getPropertiesComponent()
                .resolveProperty(key)
                .map(Integer::parseInt)
                .orElse(defaultValue);
    }

    private static Resource createHttpResource(CamelContext camelContext, String scheme, String location) {
        return new HttpResource(
                scheme, location,
                resolveTimeout(camelContext, HTTP_CONNECT_TIMEOUT_PROPERTY, DEFAULT_HTTP_CONNECT_TIMEOUT),
                resolveTimeout(camelContext, HTTP_READ_TIMEOUT_PROPERTY, DEFAULT_HTTP_READ_TIMEOUT));
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from a file.
     */
    @ResourceResolver(FileResolver.SCHEME)
    public static class FileResolver extends ResourceResolverSupport {
        public static final String SCHEME = "file";

        public FileResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            final File path = new File(getPath(remaining));
            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return path.exists();
                }

                @Override
                public URI getURI() {
                    return path.toURI();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new FileNotFoundException(path + " does not exist");
                    }
                    if (path.isDirectory()) {
                        throw new FileNotFoundException(path + " is a directory");
                    }
                    return new FileInputStream(path);
                }
            };
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from http.
     */
    @ResourceResolver(HttpResolver.SCHEME)
    public static class HttpResolver extends ResourceResolverSupport {
        public static final String SCHEME = "http";

        public HttpResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            return createHttpResource(getCamelContext(), SCHEME, location);
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from https.
     */
    @ResourceResolver(HttpsResolver.SCHEME)
    public static class HttpsResolver extends ResourceResolverSupport {
        public static final String SCHEME = "https";

        public HttpsResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            return createHttpResource(getCamelContext(), SCHEME, location);
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from the classpath.
     */
    @ResourceResolver(ClasspathResolver.SCHEME)
    public static class ClasspathResolver extends ResourceResolverSupport {
        public static final String SCHEME = "classpath";

        public ClasspathResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            final String path = getPath(remaining);
            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return getURI() != null;
                }

                @Override
                public URI getURI() {
                    URL url = getCamelContext()
                            .getClassResolver()
                            .loadResourceAsURL(path);
                    try {
                        return url != null ? url.toURI() : null;
                    } catch (URISyntaxException e) {
                        throw new IllegalArgumentException(e);
                    }
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return getCamelContext()
                            .getClassResolver()
                            .loadResourceAsStream(path);
                }
            };
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from a bean in the registry of
     * type String.
     */
    @ResourceResolver(RefResolver.SCHEME)
    public static class RefResolver extends ResourceResolverSupport {
        public static final String SCHEME = "ref";

        public RefResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            final String val = CamelContextHelper.lookup(getCamelContext(), remaining, String.class);

            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return val != null;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new IOException("There is no bean in the registry with name " + remaining + " and type String");
                    }
                    return new ByteArrayInputStream(val.getBytes());
                }
            };
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from a base64 encoded string.
     */
    @ResourceResolver(Base64Resolver.SCHEME)
    public static class Base64Resolver extends ResourceResolverSupport {
        public static final String SCHEME = "base64";

        public Base64Resolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return remaining != null;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new IOException("No base64 content defined");
                    }
                    final byte[] decoded = Base64.getDecoder().decode(remaining);
                    return new ByteArrayInputStream(decoded);
                }
            };
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from a gzip+base64 encoded
     * string.
     */
    @ResourceResolver(GzipResolver.SCHEME)
    public static class GzipResolver extends ResourceResolverSupport {
        public static final String SCHEME = "gzip";

        public GzipResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return remaining != null;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new IOException("No gzip content defined");
                    }
                    final byte[] decoded = Base64.getDecoder().decode(remaining);
                    final InputStream is = new ByteArrayInputStream(decoded);
                    return new GZIPInputStream(is);
                }
            };
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from a string.
     */
    @ResourceResolver(MemResolver.SCHEME)
    public static class MemResolver extends ResourceResolverSupport {
        public static final String SCHEME = "mem";

        public MemResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return remaining != null;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new IOException("No memory content defined");
                    }
                    return new ByteArrayInputStream(remaining.getBytes());
                }
            };
        }
    }

    /**
     * An implementation of the {@link ResourceResolver} that resolves a {@link Resource} from file system in
     * src/main/java.
     */
    @ResourceResolver(SourceResolver.SCHEME)
    public static class SourceResolver extends ResourceResolverSupport {
        public static final String SCHEME = "source";

        public SourceResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location, String remaining) {
            String dir = "src/main/java/";
            boolean test = remaining.endsWith("?test=true");
            if (test) {
                remaining = remaining.substring(0, remaining.length() - 10);
                dir = "src/test/java/";
            }
            String name = remaining.replace('.', '/') + ".java";
            final File path = new File(getPath(dir + name));
            return new ResourceSupport(SCHEME, location) {
                @Override
                public boolean exists() {
                    return path.exists();
                }

                @Override
                public URI getURI() {
                    return path.toURI();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new FileNotFoundException(path + " does not exist");
                    }
                    if (path.isDirectory()) {
                        throw new FileNotFoundException(path + " is a directory");
                    }
                    return new FileInputStream(path);
                }
            };
        }
    }

    static final class HttpResource extends ResourceSupport implements ContentTypeAware {
        private final int connectTimeout;
        private final int readTimeout;
        private String contentType;

        HttpResource(String scheme, String location, int connectTimeout, int readTimeout) {
            super(scheme, location);
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }

        @Override
        public boolean exists() {
            URLConnection connection = null;
            try {
                connection = openConnection();
                if (connection instanceof HttpURLConnection httpURLConnection) {
                    return httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK;
                }
                return connection.getContentLengthLong() > 0;
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            } finally {
                // close the http connection to avoid
                // leaking gaps in case of an exception
                if (connection instanceof HttpURLConnection httpURLConnection) {
                    httpURLConnection.disconnect();
                }
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            URLConnection con = openConnection();
            con.setUseCaches(false);
            try {
                setContentType(con.getContentType());
                return con.getInputStream();
            } catch (IOException e) {
                // close the http connection to avoid
                // leaking gaps in case of an exception
                if (con instanceof HttpURLConnection httpURLConnection) {
                    httpURLConnection.disconnect();
                }
                throw e;
            }
        }

        /**
         * Opens the connection with both timeouts applied.
         * <p/>
         * The read timeout is per read rather than for the transfer as a whole, so a large resource arriving slowly is
         * not cut off as long as bytes keep coming; it bounds the wait on a server that accepts the connection and then
         * says nothing, which is the case that otherwise stalls the caller forever.
         */
        private URLConnection openConnection() throws IOException {
            URLConnection connection = URI.create(getLocation()).toURL().openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            return connection;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }

    private static String getPath(String location) {
        // skip leading double slashes
        if (location.startsWith("//")) {
            location = location.substring(2);
        }
        String uri = tryDecodeUri(location);
        return FileUtil.compactPath(uri, '/');
    }

    private static String tryDecodeUri(String uri) {
        try {
            // try to decode as the uri may contain %20 for spaces etc
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // ignore
        }
        return uri;
    }

}
