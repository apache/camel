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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;

/**
 * ClassLoader loading from any custom class loaders that may have been added to Camel
 * {@link org.apache.camel.spi.ClassResolver}.
 */
public class CamelCustomClassLoader extends ClassLoader {

    private final CamelContext camelContext;

    public CamelCustomClassLoader(ClassLoader parent, CamelContext camelContext) {
        super(parent);
        this.camelContext = camelContext;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (ClassLoader cl : camelContext.getClassResolver().getClassLoaders()) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        throw new ClassNotFoundException(name);
    }

}
