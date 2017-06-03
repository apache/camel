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
package org.apache.camel.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * WebSphere specific resolver to handle loading annotated resources in JAR files.
 */
public class WebSpherePackageScanClassResolver extends DefaultPackageScanClassResolver {

    private final String resourcePath;

    /**
     * Constructor.
     *
     * @param resourcePath  the fixed resource path to use for fetching camel jars in WebSphere.
     */
    public WebSpherePackageScanClassResolver(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * Is the classloader from IBM and thus the WebSphere platform?
     *
     * @param loader  the classloader
     * @return  <tt>true</tt> if IBM classloader, <tt>false</tt> otherwise.
     */
    public static boolean isWebSphereClassLoader(ClassLoader loader) {
        return loader != null ? loader.getClass().getName().startsWith("com.ibm.") : false;
    }

    /**
     * Overloaded to handle specific problem with getting resources on the IBM WebSphere platform.
     * <p/>
     * WebSphere can <b>not</b> load resources if the resource to load is a folder name, such as a
     * packagename, you have to explicit name a resource that is a file.
     *
     * @param loader  the classloader
     * @param packageName   the packagename for the package to load
     * @return  URL's for the given package
     * @throws java.io.IOException is thrown by the classloader
     */
    @Override
    protected Enumeration<URL> getResources(ClassLoader loader, String packageName) throws IOException {
        // try super first, just in vase
        Enumeration<URL> enumeration = super.getResources(loader, packageName);
        if (!enumeration.hasMoreElements()) {
            log.trace("Using WebSphere workaround to load the camel jars with the annotated converters.");
            // Special WebSphere trick to load a file that exists in the JAR and then let it go from there.
            // The trick is that we just need the URL's for the .jars that contains the type
            // converters that is annotated. So by searching for this resource WebSphere is able to find
            // it and return the URL to the .jar file with the resource. Then the DefaultPackageScanClassResolver
            // can take it from there and find the classes that are annotated.
            enumeration = loader.getResources(resourcePath);
        }

        return enumeration;
    }

}
