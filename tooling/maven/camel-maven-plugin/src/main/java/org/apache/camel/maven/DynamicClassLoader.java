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
package org.apache.camel.maven;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;

class DynamicClassLoader extends URLClassLoader {

    public DynamicClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public static DynamicClassLoader createDynamicClassLoaderFromUrls(List<URL> classpathElements) {
        final URL[] urls = new URL[classpathElements.size()];
        int i = 0;
        for (Iterator<URL> it = classpathElements.iterator(); it.hasNext(); i++) {
            urls[i] = it.next();
        }
        // no parent classloader as we only want to load from the given URLs
        return new DynamicClassLoader(urls, null);
    }

}
