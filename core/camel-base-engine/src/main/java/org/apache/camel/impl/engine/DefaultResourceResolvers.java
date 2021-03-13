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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ResourceResolver;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ResourceResolverSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultResourceResolvers {
    private DefaultResourceResolvers() {
    }

    @ResourceResolver(FileResolver.SCHEME)
    public static class FileResolver extends ResourceResolverSupport {
        public static final String SCHEME = "file";
        private static final Logger LOGGER = LoggerFactory.getLogger(FileResolver.class);

        public FileResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location) {
            final String remaining = getRemaining(location);
            final Path path = Paths.get(tryDecodeUri(remaining));

            LOGGER.trace("Creating resource: {} from file system", path);

            return new Resource() {
                @Override
                public String getLocation() {
                    return location;
                }

                @Override
                public boolean exists() {
                    return Files.exists(path);
                }

                @Override
                public URI getURI() {
                    return path.toUri();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new FileNotFoundException(path.toString() + " does not exists");
                    }
                    if (Files.isDirectory(path)) {
                        throw new FileNotFoundException(path.toString() + " is a directory");
                    }

                    return Files.newInputStream(path);
                }

                @Override
                public String toString() {
                    return "Resource{" +
                           "location=" + getLocation() +
                           '}';
                }
            };
        }
    }

    @ResourceResolver(HttpResolver.SCHEME)
    public static class HttpResolver extends ResourceResolverSupport {
        public static final String SCHEME = "http";
        private static final Logger LOGGER = LoggerFactory.getLogger(HttpResolver.class);

        public HttpResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location) {
            LOGGER.trace("Creating resource: {} from HTTP", location);

            return new Resource() {
                @Override
                public String getLocation() {
                    return location;
                }

                @Override
                public boolean exists() {
                    URLConnection connection = null;

                    try {
                        connection = new URL(location).openConnection();

                        if (connection instanceof HttpURLConnection) {
                            return ((HttpURLConnection) connection).getResponseCode() == HttpURLConnection.HTTP_OK;
                        }

                        return connection.getContentLengthLong() > 0;
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    } finally {
                        // close the http connection to avoid
                        // leaking gaps in case of an exception
                        if (connection instanceof HttpURLConnection) {
                            ((HttpURLConnection) connection).disconnect();
                        }
                    }
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    URLConnection con = new URL(location).openConnection();
                    con.setUseCaches(false);

                    try {
                        return con.getInputStream();
                    } catch (IOException e) {
                        // close the http connection to avoid
                        // leaking gaps in case of an exception
                        if (con instanceof HttpURLConnection) {
                            ((HttpURLConnection) con).disconnect();
                        }
                        throw e;
                    }
                }

                @Override
                public String toString() {
                    return "Resource{" +
                           "location=" + getLocation() +
                           '}';
                }
            };
        }
    }

    @ResourceResolver(ClasspathResolver.SCHEME)
    public static class ClasspathResolver extends ResourceResolverSupport {
        public static final String SCHEME = "classpath";
        private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathResolver.class);

        public ClasspathResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location) {
            final String path = getPath(location);

            LOGGER.trace("Creating resource: {} from classpath", path);

            return new Resource() {
                @Override
                public String getLocation() {
                    return location;
                }

                @Override
                public boolean exists() {
                    return getURI() != null;
                }

                @Override
                public URI getURI() {
                    URL url = getCamelContext()
                            .adapt(ExtendedCamelContext.class)
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
                            .adapt(ExtendedCamelContext.class)
                            .getClassResolver()
                            .loadResourceAsStream(path);
                }

                @Override
                public String toString() {
                    return "Resource{" +
                           "location=" + getLocation() +
                           '}';
                }
            };
        }

        private String getPath(String location) {
            String uri = StringHelper.after(location, "classpath:");
            uri = tryDecodeUri(uri);
            uri = FileUtil.compactPath(uri, '/');

            return uri;
        }
    }

    @ResourceResolver(RefResolver.SCHEME)
    public static class RefResolver extends ResourceResolverSupport {
        public static final String SCHEME = "ref";
        private static final Logger LOGGER = LoggerFactory.getLogger(RefResolver.class);

        public RefResolver() {
            super(SCHEME);
        }

        @Override
        public Resource createResource(String location) {
            final String key = getRemaining(location);
            final String val = CamelContextHelper.lookup(getCamelContext(), key, String.class);

            LOGGER.trace("Creating resource: {} from registry", key);

            return new Resource() {
                @Override
                public String getLocation() {
                    return location;
                }

                @Override
                public boolean exists() {
                    return val != null;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!exists()) {
                        throw new IOException("There is no bean in the registry with name " + key + "and type String");
                    }

                    return new ByteArrayInputStream(val.getBytes());
                }

                @Override
                public String toString() {
                    return "Resource{" +
                           "location=" + getLocation() +
                           '}';
                }
            };
        }
    }
}
