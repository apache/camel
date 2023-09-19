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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for package scan resolvers.
 */
public abstract class BasePackageScanResolver extends ServiceSupport implements CamelContextAware {
    protected String[] acceptableSchemes = {};
    private final Set<ClassLoader> classLoaders = new LinkedHashSet<>();
    private CamelContext camelContext;

    public BasePackageScanResolver() {
        initialize();
    }

    /**
     * Performs overridable initialization logic for the package scan resolver
     */
    public void initialize() {
        try {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl != null) {
                classLoaders.add(ccl);
            }
        } catch (Exception e) {
            // Ignore this exception
            logger().warn("Cannot add ContextClassLoader from current thread due {}. This exception will be ignored.",
                    e.getMessage());
        }

        classLoaders.add(BasePackageScanResolver.class.getClassLoader());
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // ensure we also use app context class-loader
        if (camelContext != null && camelContext.getApplicationContextClassLoader() != null) {
            addClassLoader(camelContext.getApplicationContextClassLoader());
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void addClassLoader(ClassLoader classLoader) {
        classLoaders.add(classLoader);
    }

    /**
     * To specify a set of accepted schemas to use for loading resources as URL connections (besides http and https
     * schemas)
     */
    public void setAcceptableSchemes(String schemes) {
        if (schemes != null) {
            acceptableSchemes = schemes.split(";");
        }
    }

    protected boolean isAcceptableScheme(String urlPath) {
        if (urlPath != null) {
            for (String scheme : acceptableSchemes) {
                if (urlPath.startsWith(scheme)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<ClassLoader> getClassLoaders() {
        // return a new set to avoid any concurrency issues in other runtimes such as OSGi
        return Collections.unmodifiableSet(new LinkedHashSet<>(classLoaders));
    }

    // We can override this method to support the custom ResourceLocator
    protected URL customResourceLocator(URL url) throws IOException {
        // Do nothing here
        return url;
    }

    /**
     * Strategy to get the resources by the given classloader.
     * <p/>
     * Notice that in WebSphere platforms there is a {@link WebSpherePackageScanClassResolver} to take care of
     * WebSphere's oddity of resource loading.
     *
     * @param  loader      the classloader
     * @param  packageName the packagename for the package to load
     * @return             URL's for the given package
     * @throws IOException is thrown by the classloader
     */
    protected Enumeration<URL> getResources(ClassLoader loader, String packageName) throws IOException {
        logger().trace("Getting resource URL for package: {} with classloader: {}", packageName, loader);

        // If the URL is a jar, the URLClassloader.getResources() seems to require a trailing slash.  The
        // trailing slash is harmless for other URLs
        if (!packageName.isEmpty() && !packageName.endsWith("/")) {
            packageName = packageName + "/";
        }
        return loader.getResources(packageName);
    }

    protected String parseUrlPath(URL url) {
        String urlPath = url.getFile();
        urlPath = URLDecoder.decode(urlPath, StandardCharsets.UTF_8);
        if (logger().isTraceEnabled()) {
            logger().trace("Decoded urlPath: {} with protocol: {}", urlPath, url.getProtocol());
        }

        // If it's a file in a directory, trim the stupid file: spec
        if (urlPath.startsWith("file:")) {
            // file path can be temporary folder which uses characters that the URLDecoder decodes wrong
            // for example + being decoded to something else (+ can be used in temp folders on Mac OS)
            // to remedy this then create new path without using the URLDecoder
            try {
                urlPath = new URI(url.getFile()).getPath();
            } catch (URISyntaxException e) {
                // fallback to use as it was given from the URLDecoder
                // this allows us to work on Windows if users have spaces in paths
            }

            if (urlPath.startsWith("file:")) {
                urlPath = urlPath.substring(5);
            }
        }

        // osgi bundles should be skipped
        if (url.toString().startsWith("bundle:") || urlPath.startsWith("bundle:")) {
            logger().trace("Skipping OSGi bundle: {}", url);
            return null;
        }

        // bundle resource should be skipped
        if (url.toString().startsWith("bundleresource:") || urlPath.startsWith("bundleresource:")) {
            logger().trace("Skipping bundleresource: {}", url);
            return null;
        }

        // Else it's in a JAR, grab the path to the jar
        return StringHelper.before(urlPath, "!", urlPath);
    }

    protected Enumeration<URL> getUrls(String packageName, ClassLoader loader) {
        Enumeration<URL> urls;
        try {
            urls = getResources(loader, packageName);
            if (!urls.hasMoreElements()) {
                logger().trace("No URLs returned by classloader");
            }
        } catch (IOException ioe) {
            logger().warn("Cannot read package: {}", packageName, ioe);
            return null;
        }
        return urls;
    }

    /*
     * NOTE: see CAMEL-19724. We log like this instead of using a statically declared logger in order to
     * reduce the risk of dropping log messages due to slf4j log substitution behavior during its own
     * initialization.
     */
    private static final class Holder {
        static final Logger LOG = LoggerFactory.getLogger(Holder.class);
    }

    private static Logger logger() {
        return Holder.LOG;
    }

}
