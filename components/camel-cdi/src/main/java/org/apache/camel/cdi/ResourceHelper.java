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
package org.apache.camel.cdi;

import java.net.URL;

@Vetoed
final class ResourceHelper {

    private ResourceHelper() {
    }

    static URL getResource(String path, ClassLoader classLoader) {
        // Try resource loading from the ClassLoader associated with the @ImportResource annotated class
        URL url = loadResource(path, classLoader);
        if (url != null) {
            return url;
        }

        // Try resource loading from TCCL
        url = loadResource(path, Thread.currentThread().getContextClassLoader());
        if (url != null) {
            return url;
        }

        // Try resource loading from this class ClassLoader
        url = loadResource(path, ResourceHelper.class.getClassLoader());
        if (url != null) {
            return url;
        }

        // Fall back to resource loading via the system ClassLoader
        return loadResource(path, ClassLoader.getSystemClassLoader());
    }

    private static URL loadResource(String path, ClassLoader classLoader) {
        if (classLoader != null) {
            return classLoader.getResource(path);
        }
        return null;
    }
}
