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

package org.apache.camel.component.jcache.osgi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import javax.cache.Cache;

import org.apache.camel.component.jcache.JCacheConfiguration;
import org.apache.camel.component.jcache.JCacheHelper;
import org.apache.camel.component.jcache.JCacheManager;
import org.apache.camel.component.jcache.JCacheProvider;
import org.apache.camel.component.jcache.JCacheProviders;
import org.apache.camel.util.ObjectHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;

public final class OSGiCacheManager<K, V> extends JCacheManager {
    public OSGiCacheManager(JCacheConfiguration configuration) {
        super(configuration);
    }

    @Override
    public synchronized Cache<K, V> doGetCache(JCacheProvider provider) throws Exception {
        final ClassLoader jcl = getClassLoader(provider.className());
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();

        try {
            if (jcl != null) {
                Thread.currentThread().setContextClassLoader(jcl);
            }

            Cache<K, V> cache = super.doGetCache(provider);
            if (provider == JCacheProviders.hazelcast && jcl != null) {
                cache = JCacheHelper.tcclProxy(cache, Cache.class, jcl);
            }

            return cache;
        } finally {
            if (jcl != null) {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }
    }

    private ClassLoader getClassLoader(String providerName) throws Exception {
        if (providerName == null || !getConfiguration().isLookupProviders()) {
            return null;
        }

        final BundleContext bc = FrameworkUtil.getBundle(JCacheHelper.class).getBundleContext();
        final ClassLoader bcl = bc.getBundle().adapt(BundleWiring.class).getClassLoader();
        final ClassLoader acl = getConfiguration().getApplicationContextClassLoader();

        for (final Bundle bundle: bc.getBundles()) {
            URL spi = bundle.getResource("META-INF/services/javax.cache.spi.CachingProvider");
            if (spi != null) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(spi.openStream()))) {
                    if (ObjectHelper.equal(providerName, in.readLine())) {
                        return new ClassLoader(bcl) {
                            @Override
                            protected Class<?> findClass(String name) throws ClassNotFoundException {
                                try {
                                    return acl.loadClass(name);
                                } catch (ClassNotFoundException e) {
                                    return bundle.loadClass(name);
                                }
                            }
                            @Override
                            protected URL findResource(String name) {
                                URL resource = acl.getResource(name);
                                if (resource == null) {
                                    resource = bundle.getResource(name);
                                }
                                return resource;
                            }
                            @Override
                            protected Enumeration findResources(String name) throws IOException {
                                try {
                                    return acl.getResources(name);
                                } catch (IOException e) {
                                    return bundle.getResources(name);
                                }
                            }
                        };
                    }
                }
            }
        }

        return null;
    }
}
