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

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.core.osgi.utils.BundleDelegatingClassLoader;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class OsgiPackageScanClassResolver extends DefaultPackageScanClassResolver {

    private final Bundle bundle;

    public OsgiPackageScanClassResolver(BundleContext context) {
        this(context.getBundle());
    }

    public OsgiPackageScanClassResolver(Bundle bundle) {
        this.bundle = bundle;
        // add the BundleDelegatingClassLoader to the class loaders
        addClassLoader(new BundleDelegatingClassLoader(bundle));
    }

    public void find(PackageScanFilter test, String packageName, Set<Class<?>> classes) {
        packageName = packageName.replace('.', '/');
        // remember the number of classes found so far
        int classesSize = classes.size();
        // look in osgi bundles
        loadImplementationsInBundle(test, packageName, classes);
        // if we did not find any new, then fallback to use regular non bundle class loading
        if (classes.size() == classesSize) {
            // Using the non-OSGi classloaders as a fallback
            // this is necessary when use JBI packaging for servicemix-camel SU
            // so that we get chance to use SU classloader to scan packages in the SU
            log.trace("Cannot find any classes in bundles, not trying regular classloaders scanning: {}", packageName);
            for (ClassLoader classLoader : super.getClassLoaders()) {
                if (!isOsgiClassloader(classLoader)) {
                    find(test, packageName, classLoader, classes);
                }
            }  
        }
    }
    
    private static boolean isOsgiClassloader(ClassLoader loader) {
        try {
            Method mth = loader.getClass().getMethod("getBundle", new Class[] {});
            if (mth != null) {
                return true;
            }
        } catch (NoSuchMethodException e) {
            // ignore its not an osgi loader
        }
        return false;
    }
    
    private void loadImplementationsInBundle(PackageScanFilter test, String packageName, Set<Class<?>> classes) {       
        Set<String> urls = getImplementationsInBundle(test, packageName);
        if (urls != null) {
            for (String url : urls) {
                // substring to avoid leading slashes
                addIfMatching(test, url, classes);
            }
        }
    }

    private Set<String> getImplementationsInBundle(PackageScanFilter test, String packageName) {
        Bundle[] bundles;
        if (bundle.getBundleContext() != null) {
            bundles = bundle.getBundleContext().getBundles();
        } else {
            bundles = new Bundle[]{bundle};
        }
        Set<String> urls = new LinkedHashSet<String>();
        for (Bundle bd : bundles) {
            log.trace("Searching in bundle: {}", bd);
            try {
                Enumeration<URL> paths = bd.findEntries("/" + packageName, "*.class", true);
                while (paths != null && paths.hasMoreElements()) {
                    URL path = paths.nextElement();
                    String pathString = path.getPath();
                    String urlString = pathString.substring(pathString.indexOf(packageName));
                    urls.add(urlString);
                    log.trace("Added url: {}", urlString);
                }
            } catch (Throwable t) {
                log.warn("Cannot search in bundle: " + bundle + " for classes matching criteria: " + test + " due: "
                        + t.getMessage() + ". This exception will be ignored.", t);
            }
        }
        return urls;
    }

}
