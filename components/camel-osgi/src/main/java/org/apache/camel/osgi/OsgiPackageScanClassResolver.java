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
package org.apache.camel.osgi;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.util.BundleDelegatingClassLoader;

public class OsgiPackageScanClassResolver extends DefaultPackageScanClassResolver {
    private Bundle bundle;
    
    public OsgiPackageScanClassResolver(BundleContext context) {
        bundle = context.getBundle();
    }

    public Set<ClassLoader> getClassLoaders() {
        Set<ClassLoader> classLoaders = super.getClassLoaders();
        // Using the Activator's bundle to make up a class loader
        ClassLoader osgiLoader = BundleDelegatingClassLoader.createBundleClassLoaderFor(bundle);
        classLoaders.add(osgiLoader);
        return classLoaders;
    }
    
    public void find(PackageScanFilter test, String packageName, Set<Class> classes) {
        packageName = packageName.replace('.', '/');
        Set<ClassLoader> set = getClassLoaders();
        ClassLoader osgiClassLoader = getOsgiClassLoader(set);
        int classesSize = classes.size(); 
        if (osgiClassLoader != null) {
            // if we have an osgi bundle loader use this one first
            LOG.debug("Using only osgi bundle classloader");
            findInOsgiClassLoader(test, packageName, osgiClassLoader, classes);
        }
        
        if (classes.size() == classesSize) {
            // Using the regular classloaders as a fallback
            LOG.debug("Using only regular classloaders");
            for (ClassLoader classLoader : set.toArray(new ClassLoader[set.size()])) {
                if (!isOsgiClassloader(classLoader)) {
                    find(test, packageName, classLoader, classes);
                }
            }
        }
    }

    private void findInOsgiClassLoader(PackageScanFilter test, String packageName, ClassLoader osgiClassLoader, Set<Class> classes) {
        try {
            Method mth = osgiClassLoader.getClass().getMethod("getBundle", new Class[]{});
            if (mth != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Loading from osgi bundle using classloader: " + osgiClassLoader);
                }
                loadImplementationsInBundle(test, packageName, osgiClassLoader, mth, classes);
            }
        } catch (NoSuchMethodException e) {
            LOG.warn("It's not an osgi bundle classloader: " + osgiClassLoader);
        }
    }

    /**
     * Gets the osgi classloader if any in the given set
     */
    private static ClassLoader getOsgiClassLoader(Set<ClassLoader> set) {
        for (ClassLoader loader : set) {
            if (isOsgiClassloader(loader)) {
                return loader;
            }
        }
        return null;
    }

    /**
     * Is it an osgi classloader
     */
    private static boolean isOsgiClassloader(ClassLoader loader) {
        try {
            Method mth = loader.getClass().getMethod("getBundle", new Class[]{});
            if (mth != null) {
                return true;
            }
        } catch (NoSuchMethodException e) {
            // ignore its not an osgi loader
        }
        return false;
    }
    
    private void loadImplementationsInBundle(PackageScanFilter test, String packageName, ClassLoader loader, Method mth, Set<Class> classes) {
        // Use an inner class to avoid a NoClassDefFoundError when used in a non-osgi env
        Set<String> urls = OsgiUtil.getImplementationsInBundle(test, packageName, loader, mth);
        if (urls != null) {
            for (String url : urls) {
                // substring to avoid leading slashes
                addIfMatching(test, url, classes);
            }
        }
    }

    private static final class OsgiUtil {
        private OsgiUtil() {
            // Helper class
        }
        @SuppressWarnings("unchecked")
        static Set<String> getImplementationsInBundle(PackageScanFilter test, String packageName, ClassLoader loader, Method mth) {
            try {
                org.osgi.framework.Bundle bundle = (org.osgi.framework.Bundle) mth.invoke(loader);
                org.osgi.framework.Bundle[] bundles = bundle.getBundleContext().getBundles();
                Set<String> urls = new HashSet<String>();
                for (org.osgi.framework.Bundle bd : bundles) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Searching in bundle:" + bd);
                    }
                    Enumeration<URL> paths = bd.findEntries("/" + packageName, "*.class", true);
                    while (paths != null && paths.hasMoreElements()) {
                        URL path = paths.nextElement();
                        String pathString = path.getPath();
                        String urlString = pathString.substring(pathString.indexOf(packageName));
                        urls.add(urlString);
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("added url " + urlString);
                        }
                    }
                }
                return urls;
            } catch (Throwable t) {
                LOG.error("Could not search osgi bundles for classes matching criteria: " + test
                          + "due to an Exception: " + t.getMessage());
                return null;
            }
        }
    }

}
