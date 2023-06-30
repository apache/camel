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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
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
     * @param  uri the URI
     * @return     <tt>true</tt> if the URI starts with a scheme
     */
    public static boolean hasScheme(String uri) {
        if (uri == null) {
            return false;
        }

        return uri.startsWith("file:") || uri.startsWith("classpath:") || uri.startsWith("ref:") ||
                uri.startsWith("bean:") || uri.startsWith("http:") || uri.startsWith("https:");
    }

    /**
     * Gets the scheme from the URI (e.g. file:, classpath: or http:)
     *
     * @param  uri the uri
     * @return     the scheme, or <tt>null</tt> if no scheme
     */
    public static String getScheme(String uri) {
        if (hasScheme(uri)) {
            return uri.substring(0, uri.indexOf(':') + 1);
        } else {
            return null;
        }
    }

    /**
     * Resolves the mandatory resource.
     * <p/>
     * The resource uri can refer to the following systems to be loaded from
     * <ul>
     * <il>file:nameOfFile - to refer to the file system</il> <il>classpath:nameOfFile - to refer to the classpath
     * (default)</il> <il>http:uri - to load the resource using HTTP</il> <il>ref:nameOfBean - to lookup the resource in
     * the {@link org.apache.camel.spi.Registry}</il> <il>bean:nameOfBean.methodName or bean:nameOfBean::methodName - to
     * lookup a bean in the {@link org.apache.camel.spi.Registry} and call the method</il> <il><customProtocol>:uri - to
     * lookup the resource using a custom {@link java.net.URLStreamHandler} registered for the <customProtocol>, on how
     * to register it @see java.net.URL#URL(java.lang.String, java.lang.String, int, java.lang.String)</il>
     * </ul>
     * If no prefix has been given, then the resource is loaded from the classpath
     * <p/>
     * If possible recommended to use {@link #resolveMandatoryResourceAsUrl(CamelContext, String)}
     *
     * @param  camelContext        the Camel Context
     * @param  uri                 URI of the resource
     * @return                     the resource as an {@link InputStream}. Remember to close this stream after usage.
     * @throws java.io.IOException is thrown if the resource file could not be found or loaded as {@link InputStream}
     */
    public static InputStream resolveMandatoryResourceAsInputStream(CamelContext camelContext, String uri) throws IOException {
        InputStream is = resolveResourceAsInputStream(camelContext, uri);
        if (is == null) {
            String resolvedName = resolveUriPath(uri);
            throw new FileNotFoundException("Cannot find resource: " + resolvedName + " for URI: " + uri);
        } else {
            return is;
        }
    }

    /**
     * Resolves the resource.
     * <p/>
     * If possible recommended to use {@link #resolveMandatoryResourceAsUrl(CamelContext, String)}
     *
     * @param  camelContext        the camel context
     * @param  uri                 URI of the resource
     * @return                     the resource as an {@link InputStream}. Remember to close this stream after usage. Or
     *                             <tt>null</tt> if not found.
     * @throws java.io.IOException is thrown if error loading the resource
     */
    public static InputStream resolveResourceAsInputStream(CamelContext camelContext, String uri) throws IOException {
        final Resource resource = resolveResource(camelContext, uri);
        return resource.getInputStream();
    }

    /**
     * Resolves the mandatory resource.
     *
     * @param  camelContext                   the camel context
     * @param  uri                            uri of the resource
     * @return                                the resource as an {@link java.net.URL}.
     * @throws java.io.FileNotFoundException  is thrown if the resource file could not be found
     * @throws java.net.MalformedURLException if the URI is malformed
     */
    public static URL resolveMandatoryResourceAsUrl(CamelContext camelContext, String uri)
            throws FileNotFoundException, MalformedURLException {
        URL url = resolveResourceAsUrl(camelContext, uri);
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
     * @param  camelContext                   the camel context
     * @param  uri                            uri of the resource
     * @return                                the resource as an {@link java.net.URL}. Or <tt>null</tt> if not found.
     * @throws java.net.MalformedURLException if the URI is malformed
     */
    public static URL resolveResourceAsUrl(CamelContext camelContext, String uri) throws MalformedURLException {
        final Resource resource = resolveResource(camelContext, uri);
        return resource.getURL();
    }

    /**
     * Resolves a mandatory resource.
     *
     * @param  camelContext          the camel context
     * @param  uri                   the uri of the resource
     * @return                       the {@link Resource}
     * @throws FileNotFoundException if the resource could not be found
     */
    public static Resource resolveMandatoryResource(CamelContext camelContext, String uri) throws FileNotFoundException {
        final Resource resource = resolveResource(camelContext, uri);
        if (resource == null) {
            String resolvedName = resolveUriPath(uri);
            throw new FileNotFoundException("Cannot find resource: " + resolvedName + " for URI: " + uri);
        }
        return resource;
    }

    /**
     * Resolves a resource.
     *
     * @param  camelContext the camel context
     * @param  uri          the uri of the resource
     * @return              the {@link Resource}. Or <tt>null</tt> if not found
     */
    public static Resource resolveResource(CamelContext camelContext, String uri) {
        final ResourceLoader loader = PluginHelper.getResourceLoader(camelContext);
        return loader.resolveResource(uri);
    }

    /**
     * Is the given uri a classpath uri?
     *
     * @param  uri the uri
     * @return     <tt>true</tt> if the uri starts with <tt>classpath:</tt> or has no scheme and therefore would
     *             otherwise be loaded from classpath by default.
     */
    public static boolean isClasspathUri(String uri) {
        if (ObjectHelper.isEmpty(uri)) {
            return false;
        }
        return uri.startsWith("classpath:") || uri.indexOf(':') == -1;
    }

    /**
     * Is the given uri a http uri?
     *
     * @param  uri the uri
     * @return     <tt>true</tt> if the uri starts with <tt>http:</tt> or <tt>https:</tt>
     */
    public static boolean isHttpUri(String uri) {
        if (ObjectHelper.isEmpty(uri)) {
            return false;
        }
        return uri.startsWith("http:") || uri.startsWith("https:");
    }

    /**
     * Appends the parameters to the given uri
     *
     * @param  uri                the uri
     * @param  parameters         the additional parameters (will clear the map)
     * @return                    a new uri with the additional parameters appended
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
     * Helper operation used to remove relative path notation from resources. Most critical for resources on the
     * Classpath as resource loaders will not resolve the relative paths correctly.
     *
     * @param  name the name of the resource to load
     * @return      the modified or unmodified string if there were no changes
     */
    private static String resolveUriPath(String name) {
        // compact the path and use / as separator as that's used for loading resources on the classpath
        return FileUtil.compactPath(name, '/');
    }

    /**
     * Find resources from the file system using Ant-style path patterns (skips hidden files, or files from hidden
     * folders).
     *
     * @param  root      the starting file
     * @param  pattern   the Ant pattern
     * @return           set of files matching the given pattern
     * @throws Exception is thrown if IO error
     */
    public static Set<Path> findInFileSystem(Path root, String pattern) throws Exception {
        try (Stream<Path> path = Files.walk(root)) {
            return path
                    .filter(Files::isRegularFile)
                    .filter(entry -> {
                        Path relative = root.relativize(entry);
                        String str = relative.toString().replaceAll(Pattern.quote(File.separator),
                                AntPathMatcher.DEFAULT_PATH_SEPARATOR);
                        // skip files in hidden folders
                        boolean hidden = str.startsWith(".") || str.contains(AntPathMatcher.DEFAULT_PATH_SEPARATOR + ".");
                        if (!hidden) {
                            boolean match = AntPathMatcher.INSTANCE.match(pattern, str);
                            LOG.debug("Found resource: {} matching pattern: {} -> {}", entry, pattern, match);
                            return match;
                        } else {
                            return false;
                        }
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /**
     * Create a {@link Resource} from bytes.
     *
     * @param  location a virtual location
     * @param  content  the resource content
     * @return          a resource wrapping the given byte array
     */
    public static Resource fromBytes(String location, byte[] content) {
        return new ResourceSupport("mem", location) {
            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }
        };
    }

    /**
     * Create a {@link Resource} from a {@link String}.
     * </p>
     * The implementation delegates to {@link #fromBytes(String, byte[])} by encoding the string as bytes with
     * {@link String#getBytes(Charset)} and {@link StandardCharsets#UTF_8} as charset.
     *
     * @param  location a virtual location
     * @param  content  the resource content
     * @return          a resource wrapping the given {@link String}
     */
    public static Resource fromString(String location, String content) {
        return fromBytes(location, content.getBytes(StandardCharsets.UTF_8));
    }
}
