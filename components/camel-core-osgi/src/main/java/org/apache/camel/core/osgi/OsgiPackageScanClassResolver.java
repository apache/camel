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

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.core.osgi.utils.BundleDelegatingClassLoader;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class OsgiPackageScanClassResolver extends DefaultPackageScanClassResolver {
    private Bundle bundle;
    
    public OsgiPackageScanClassResolver(Bundle bundle) {
        this.bundle = bundle;
    }

    public OsgiPackageScanClassResolver(BundleContext context) {
        bundle = context.getBundle();
    }

    public Set<ClassLoader> getClassLoaders() {
        // now we just use bundle classloader to load the class
        return Collections.<ClassLoader>singleton(new BundleDelegatingClassLoader(bundle));
    }
    
    public void find(PackageScanFilter test, String packageName, Set<Class<?>> classes) {
        packageName = packageName.replace('.', '/');
        loadImplementationsInBundle(test, packageName, classes);
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

    @SuppressWarnings("unchecked")
    private Set<String> getImplementationsInBundle(PackageScanFilter test, String packageName) {
        try {
            Bundle[] bundles;
            if (bundle.getBundleContext()!= null) {
                bundles = bundle.getBundleContext().getBundles();
            } else {
                bundles = new Bundle[]{bundle};
            }
            Set<String> urls = new HashSet<String>();
            for (Bundle bd : bundles) {            
                if (log.isTraceEnabled()) {
                    log.trace("Searching in bundle:" + bd);
                }            
                Enumeration<URL> paths = bd.findEntries("/" + packageName, "*.class", true);
                while (paths != null && paths.hasMoreElements()) {
                    URL path = paths.nextElement();                
                    String pathString = path.getPath();
                    String urlString = pathString.substring(pathString.indexOf(packageName));
                    urls.add(urlString);
                    if (log.isTraceEnabled()) {
                        log.trace("Added url: " + urlString);
                    }
                }
            }
            return urls;
        } catch (Throwable t) {
            log.error("Could not search osgi bundles for classes matching criteria: " + test + "due to an Exception: " + t.getMessage());
            return null;
        }
    }

}
