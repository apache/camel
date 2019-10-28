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
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for package scan resolvers.
 */
public abstract class BasePackageScanResolver extends ServiceSupport implements CamelContextAware {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected String[] acceptableSchemes = {};
    private final Set<ClassLoader> classLoaders = new LinkedHashSet<>();
    private CamelContext camelContext;

    public BasePackageScanResolver() {
        try {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl != null) {
                log.trace("Adding ContextClassLoader from current thread: {}", ccl);
                classLoaders.add(ccl);
            }
        } catch (Exception e) {
            // Ignore this exception
            log.warn("Cannot add ContextClassLoader from current thread due {}. This exception will be ignored.", e.getMessage());
        }

        classLoaders.add(BasePackageScanResolver.class.getClassLoader());
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
     * To specify a set of accepted schemas to use for loading resources as URL connections
     * (besides http and https schemas)
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
     * Notice that in WebSphere platforms there is a {@link WebSpherePackageScanClassResolver}
     * to take care of WebSphere's oddity of resource loading.
     *
     * @param loader  the classloader
     * @param packageName   the packagename for the package to load
     * @return  URL's for the given package
     * @throws IOException is thrown by the classloader
     */
    protected Enumeration<URL> getResources(ClassLoader loader, String packageName) throws IOException {
        log.trace("Getting resource URL for package: {} with classloader: {}", packageName, loader);

        // If the URL is a jar, the URLClassloader.getResources() seems to require a trailing slash.  The
        // trailing slash is harmless for other URLs
        if (!packageName.endsWith("/")) {
            packageName = packageName + "/";
        }
        return loader.getResources(packageName);
    }

}
