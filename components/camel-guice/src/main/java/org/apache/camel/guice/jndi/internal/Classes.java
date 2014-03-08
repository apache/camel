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
package org.apache.camel.guice.jndi.internal;


public final class Classes {
    private Classes() {
        // Helper class
    }

    /**
     * Attempts to load the class of the given name from the thread context
     * class loader first then the given class loader second
     * 
     * @param name
     *            the name of the class
     * @param loader
     *            the class loader to use if the thread context class loader
     *            cannot find the class
     * @return the class loaded
     * @throws ClassNotFoundException
     *             if the class could not be found
     */
    public static Class<?> loadClass(String name, ClassLoader loader)
        throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                try {
                    return loader.loadClass(name);
                } catch (ClassNotFoundException e1) {
                    throw e;
                }
            }
        }
        return null;
    }
}
