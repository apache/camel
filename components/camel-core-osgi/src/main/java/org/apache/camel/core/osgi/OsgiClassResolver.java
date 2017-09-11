/**
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
package org.apache.camel.core.osgi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Using the bundle of CamelContext to load the class */
public class OsgiClassResolver extends DefaultClassResolver {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiClassResolver.class);

    private final CamelContext camelContext;
    private final BundleContext bundleContext;

    public OsgiClassResolver(CamelContext camelContext, BundleContext context) {
        super(camelContext);
        this.camelContext = camelContext;
        this.bundleContext = context;
    }

    @Override
    public Class<?> resolveClass(String name) {
        LOG.trace("Resolve class {}", name);
        name = ObjectHelper.normalizeClassName(name);
        if (ObjectHelper.isEmpty(name)) {
            return null;
        }
        // we need to avoid the NPE issue of loading the class
        Class<?> clazz = ObjectHelper.loadSimpleType(name);
        if (clazz == null) {
            clazz = doLoadClass(name, bundleContext.getBundle());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Loading class {} using BundleContext {} -> {}", new Object[]{name, bundleContext.getBundle(), clazz});
            }
        }
        if (clazz == null && camelContext != null) {
            // fallback and load class using the application context classloader
            clazz = super.loadClass(name, camelContext.getApplicationContextClassLoader());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Loading class {} using CamelContext {} -> {}", new Object[]{name, camelContext, clazz});
            }
        }
        return clazz;
    }

    @Override
    public <T> Class<T> resolveClass(String name, Class<T> type) {
        return CastUtils.cast(resolveClass(name));
    }

    @Override
    public InputStream loadResourceAsStream(String uri) {
        ObjectHelper.notEmpty(uri, "uri");

        String resolvedName = resolveUriPath(uri);
        URL url = loadResourceAsURL(resolvedName);
        InputStream answer = null;
        if (url != null) {
            try {
                answer = url.openStream();
            } catch (IOException ex) {
                throw new RuntimeException("Cannot load resource: " + uri, ex);
            }
        }

        // fallback to default as OSGi may have issues loading resources
        if (answer == null) {
            answer = super.loadResourceAsStream(uri);
        }
        return answer;
    }

    @Override
    public URL loadResourceAsURL(String uri) {
        ObjectHelper.notEmpty(uri, "uri");
        String resolvedName = resolveUriPath(uri);
        URL answer = bundleContext.getBundle().getResource(resolvedName);

        // fallback to default as OSGi may have issues loading resources
        if (answer == null) {
            answer = super.loadResourceAsURL(uri);
        }
        return answer;
    }

    @Override
    public Enumeration<URL> loadResourcesAsURL(String uri) {
        ObjectHelper.notEmpty(uri, "uri");
        try {
            String resolvedName = resolveUriPath(uri);
            return bundleContext.getBundle().getResources(resolvedName);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load resource: " + uri, e);
        }
    }

    @Override
    public Enumeration<URL> loadAllResourcesAsURL(String uri) {
        ObjectHelper.notEmpty(uri, "uri");
        Vector<URL> answer = new Vector<URL>();

        try {
            String resolvedName = resolveUriPath(uri);

            Enumeration<URL> e = bundleContext.getBundle().getResources(resolvedName);
            while (e != null && e.hasMoreElements()) {
                answer.add(e.nextElement());
            }

            String path = FileUtil.onlyPath(uri);
            String name = FileUtil.stripPath(uri);
            if (path != null && name != null) {
                for (Bundle bundle : bundleContext.getBundles()) {
                    LOG.trace("Finding all entries in path: {} with pattern: {}", path, name);
                    e = bundle.findEntries(path, name, false);
                    while (e != null && e.hasMoreElements()) {
                        answer.add(e.nextElement());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot load resource: " + uri, e);
        }

        return answer.elements();
    }

    protected Class<?> doLoadClass(String name, Bundle loader) {
        ObjectHelper.notEmpty(name, "name");
        Class<?> answer = null;
        // Try to use the camel context's bundle's classloader to load the class
        if (loader != null) {
            try {
                answer = loader.loadClass(name);
            } catch (ClassNotFoundException e) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Cannot load class: " + name + " using classloader: " + loader + ". This exception will be ignored.", e);
                }
            }
        }
        return answer;
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

    
}
