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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for loading resources on the classpath or file system.
 */
public final class ResourceHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceHelper.class);

    private ResourceHelper() {
        // utility class
    }

    /**
     * Determines whether the URI has a scheme (e.g. file:, classpath: or http:)
     *
     * @param uri the URI
     * @return <tt>true</tt> if the URI starts with a scheme
     */
    public static boolean hasScheme(String uri) {
        if (uri == null) {
            return false;
        }

        return uri.startsWith("file:") || uri.startsWith("classpath:") || uri.startsWith("http:");
    }

    /**
     * Gets the scheme from the URI (e.g. file:, classpath: or http:)
     *
     * @param uri  the uri
     * @return the scheme, or <tt>null</tt> if no scheme
     */
    public static String getScheme(String uri) {
        if (hasScheme(uri)) {
            return uri.substring(0, uri.indexOf(":") + 1);
        } else {
            return null;
        }
    }

    /**
     * Resolves the mandatory resource.
     * <p/>
     * The resource uri can refer to the following systems to be loaded from
     * <ul>
     *     <il>file:nameOfFile - to refer to the file system</il>
     *     <il>classpath:nameOfFile - to refer to the classpath (default)</il>
     *     <il>http:uri - to load the resource using HTTP</il>
     *     <il>ref:nameOfBean - to lookup the resource in the {@link org.apache.camel.spi.Registry}</il>
     *     <il>bean:nameOfBean.methodName or bean:nameOfBean::methodName - to lookup a bean in the {@link org.apache.camel.spi.Registry} and call the method</il>
     *     <il><customProtocol>:uri - to lookup the resource using a custom {@link java.net.URLStreamHandler} registered for the <customProtocol>,
     *     on how to register it @see java.net.URL#URL(java.lang.String, java.lang.String, int, java.lang.String)</il>
     * </ul>
     * If no prefix has been given, then the resource is loaded from the classpath
     * <p/>
     * If possible recommended to use {@link #resolveMandatoryResourceAsUrl(org.apache.camel.spi.ClassResolver, String)}
     *
     * @param camelContext the Camel Context
     * @param uri URI of the resource
     * @return the resource as an {@link InputStream}.  Remember to close this stream after usage.
     * @throws java.io.IOException is thrown if the resource file could not be found or loaded as {@link InputStream}
     */
    public static InputStream resolveMandatoryResourceAsInputStream(CamelContext camelContext, String uri) throws IOException {
        if (uri.startsWith("ref:")) {
            String ref = uri.substring(4);
            String value = CamelContextHelper.mandatoryLookup(camelContext, ref, String.class);
            return new ByteArrayInputStream(value.getBytes());
        } else if (uri.startsWith("bean:")) {
            String bean = uri.substring(5);
            Exchange dummy = new DefaultExchange(camelContext);
            Object out = camelContext.resolveLanguage("bean").createExpression(bean).evaluate(dummy, Object.class);
            if (dummy.getException() != null) {
                IOException io = new IOException("Cannot find resource: " + uri + " from calling the bean");
                io.initCause(dummy.getException());
                throw io;
            }
            if (out != null) {
                InputStream is = camelContext.getTypeConverter().tryConvertTo(InputStream.class, dummy, out);
                if (is == null) {
                    String text = camelContext.getTypeConverter().tryConvertTo(String.class, dummy, out);
                    if (text != null) {
                        return new ByteArrayInputStream(text.getBytes());
                    }
                } else {
                    return is;
                }
            } else {
                throw new IOException("Cannot find resource: " + uri + " from calling the bean");
            }
        }

        InputStream is = resolveResourceAsInputStream(camelContext.getClassResolver(), uri);
        if (is == null) {
            String resolvedName = resolveUriPath(uri);
            throw new FileNotFoundException("Cannot find resource: " + resolvedName + " in classpath for URI: " + uri);
        } else {
            return is;
        }
    }

    /**
     * Resolves the resource.
     * <p/>
     * If possible recommended to use {@link #resolveMandatoryResourceAsUrl(org.apache.camel.spi.ClassResolver, String)}
     *
     * @param classResolver the class resolver to load the resource from the classpath
     * @param uri URI of the resource
     * @return the resource as an {@link InputStream}. Remember to close this stream after usage. Or <tt>null</tt> if not found.
     * @throws java.io.IOException is thrown if error loading the resource
     */
    public static InputStream resolveResourceAsInputStream(ClassResolver classResolver, String uri) throws IOException {
        if (uri.startsWith("file:")) {
            uri = StringHelper.after(uri, "file:");
            uri = tryDecodeUri(uri);
            LOG.trace("Loading resource: {} from file system", uri);
            return new FileInputStream(uri);
        } else if (uri.startsWith("http:")) {
            URL url = new URL(uri);
            LOG.trace("Loading resource: {} from HTTP", uri);
            URLConnection con = url.openConnection();
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
        } else if (uri.startsWith("classpath:")) {
            uri = StringHelper.after(uri, "classpath:");
            uri = tryDecodeUri(uri);
        } else if (uri.contains(":")) {
            LOG.trace("Loading resource: {} with UrlHandler for protocol {}", uri, uri.split(":")[0]);
            URL url = new URL(uri);
            URLConnection con = url.openConnection();
            return con.getInputStream();
        }

        // load from classpath by default
        String resolvedName = resolveUriPath(uri);
        LOG.trace("Loading resource: {} from classpath", resolvedName);
        return classResolver.loadResourceAsStream(resolvedName);
    }

    /**
     * Resolves the mandatory resource.
     *
     * @param classResolver the class resolver to load the resource from the classpath
     * @param uri uri of the resource
     * @return the resource as an {@link java.net.URL}.
     * @throws java.io.FileNotFoundException is thrown if the resource file could not be found
     * @throws java.net.MalformedURLException if the URI is malformed
     */
    public static URL resolveMandatoryResourceAsUrl(ClassResolver classResolver, String uri) throws FileNotFoundException, MalformedURLException {
        URL url = resolveResourceAsUrl(classResolver, uri);
        if (url == null) {
            String resolvedName = resolveUriPath(uri);
            throw new FileNotFoundException("Cannot find resource: " + resolvedName + " in classpath for URI: " + uri);
        } else {
            return url;
        }
    }

    /**
     * Resolves the resource.
     *
     * @param classResolver the class resolver to load the resource from the classpath
     * @param uri uri of the resource
     * @return the resource as an {@link java.net.URL}. Or <tt>null</tt> if not found.
     * @throws java.net.MalformedURLException if the URI is malformed
     */
    public static URL resolveResourceAsUrl(ClassResolver classResolver, String uri) throws MalformedURLException {
        if (uri.startsWith("file:")) {
            // check if file exists first
            String name = StringHelper.after(uri, "file:");
            uri = tryDecodeUri(uri);
            LOG.trace("Loading resource: {} from file system", uri);
            File file = new File(name);
            if (!file.exists()) {
                return null;
            }
            return new URL(uri);
        } else if (uri.startsWith("http:")) {
            LOG.trace("Loading resource: {} from HTTP", uri);
            return new URL(uri);
        } else if (uri.startsWith("classpath:")) {
            uri = StringHelper.after(uri, "classpath:");
            uri = tryDecodeUri(uri);
        } else if (uri.contains(":")) {
            LOG.trace("Loading resource: {} with UrlHandler for protocol {}", uri, uri.split(":")[0]);
            return new URL(uri);
        }

        // load from classpath by default
        String resolvedName = resolveUriPath(uri);
        LOG.trace("Loading resource: {} from classpath", resolvedName);
        return classResolver.loadResourceAsURL(resolvedName);
    }

    /**
     * Is the given uri a http uri?
     *
     * @param uri the uri
     * @return <tt>true</tt> if the uri starts with <tt>http:</tt> or <tt>https:</tt>
     */
    public static boolean isHttpUri(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.startsWith("http:") || uri.startsWith("https:");
    }

    /**
     * Appends the parameters to the given uri
     *
     * @param uri the uri
     * @param parameters the additional parameters (will clear the map)
     * @return a new uri with the additional parameters appended
     * @throws URISyntaxException is thrown if the uri is invalid
     */
    public static String appendParameters(String uri, Map<String, Object> parameters) throws URISyntaxException {
        // add additional parameters to the resource uri
        if (!parameters.isEmpty()) {
            String query = URISupport.createQueryString(parameters);
            URI u = new URI(uri);
            u = URISupport.createURIWithQuery(u, query);
            parameters.clear();
            return u.toString();
        } else {
            return uri;
        }
    }

    /**
     * Helper operation used to remove relative path notation from
     * resources.  Most critical for resources on the Classpath
     * as resource loaders will not resolve the relative paths correctly.
     *
     * @param name the name of the resource to load
     * @return the modified or unmodified string if there were no changes
     */
    private static String resolveUriPath(String name) {
        // compact the path and use / as separator as that's used for loading resources on the classpath
        return FileUtil.compactPath(name, '/');
    }

    /**
     * Tries decoding the uri.
     *
     * @param uri the uri
     * @return the decoded uri, or the original uri
     */
    private static String tryDecodeUri(String uri) {
        try {
            // try to decode as the uri may contain %20 for spaces etc
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (Exception e) {
            LOG.trace("Error URL decoding uri using UTF-8 encoding: {}. This exception is ignored.", uri);
            // ignore
        }
        return uri;
    }

    /**
     * Find resources from the file system using Ant-style path patterns.
     *
     * @param root the starting file
     * @param pattern the Ant pattern
     * @return a list of files matching the given pattern
     * @throws Exception
     */
    public static Set<Path> findInFileSystem(Path root, String pattern) throws Exception {
        try (Stream<Path> path = Files.walk(root)) {
            return path
                .filter(Files::isRegularFile)
                .filter(entry -> {
                    Path relative = root.relativize(entry);
                    boolean match = AntPathMatcher.INSTANCE.match(pattern, relative.toString());
                    LOG.debug("Found resource: {} matching pattern: {} -> {}", entry.toString(), pattern, match);
                    return match;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
