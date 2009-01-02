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

import java.util.Set;

import org.apache.camel.util.ResolverUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.util.BundleDelegatingClassLoader;

public class OsgiResolverUtil extends ResolverUtil {
    private Bundle bundle;
    
    public OsgiResolverUtil(BundleContext context) {
        bundle = context.getBundle();
    }
    
    /**
     * Returns the classloaders that will be used for scanning for classes. 
     * Here we just add BundleDelegatingClassLoader here
     *
     * @return the ClassLoader instances that will be used to scan for classes
     */
    public Set<ClassLoader> getClassLoaders() {
        Set<ClassLoader> classLoaders = super.getClassLoaders();
        // Using the Activator's bundle to make up a class loader
        ClassLoader osgiLoader = BundleDelegatingClassLoader.createBundleClassLoaderFor(bundle);
        classLoaders.add(osgiLoader);
        return classLoaders;
    }

}
