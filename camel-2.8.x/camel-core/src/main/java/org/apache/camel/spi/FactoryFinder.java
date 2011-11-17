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
package org.apache.camel.spi;

import java.io.IOException;
import java.util.List;

import org.apache.camel.NoFactoryAvailableException;

/**
 * Finder to find factories from the resource classpath, usually <b>META-INF/services/org/apache/camel/</b>.
 *
 * @version 
 */
public interface FactoryFinder {

    /**
     * Gets the resource classpath.
     *
     * @return the resource classpath.
     */
    String getResourcePath();

    /**
     * Creates a new class instance using the key to lookup
     *
     * @param key is the key to add to the path to find a text file containing the factory name
     * @return a newly created instance
     * @throws org.apache.camel.NoFactoryAvailableException is thrown if no factories exist for the given key
     */
    Object newInstance(String key) throws NoFactoryAvailableException;

    /**
     * Creates a new class instance using the key to lookup
     *
     * @param key is the key to add to the path to find a text file containing the factory name
     * @param injector injector to use
     * @param type expected type
     * @return a newly created instance as the expected type
     * @throws ClassNotFoundException is thrown if not found
     * @throws java.io.IOException is thrown if loading the class or META-INF file not found
     */
    <T> List<T> newInstances(String key, Injector injector, Class<T> type) throws ClassNotFoundException, IOException;

    /**
     * Finds the given factory class using the the key to lookup.
     *
     * @param key is the key to add to the path to find a text file containing the factory name
     * @return the factory class
     * @throws ClassNotFoundException is thrown if class not found
     * @throws java.io.IOException is thrown if loading the class or META-INF file not found
     */
    Class<?> findClass(String key) throws ClassNotFoundException, IOException;

    /**
     * Finds the given factory class using the the key to lookup.
     *
     * @param key is the key to add to the path to find a text file containing the factory name
     * @param propertyPrefix prefix on key
     * @return the factory class
     * @throws ClassNotFoundException is thrown if not found
     * @throws java.io.IOException is thrown if loading the class or META-INF file not found
     */
    Class<?> findClass(String key, String propertyPrefix) throws ClassNotFoundException, IOException;

    /**
     * Finds the given factory class using the the key to lookup.
     *
     * @param key is the key to add to the path to find a text file containing the factory name
     * @param propertyPrefix prefix on key
     * @param clazz the class which is used for checking compatible
     * @return the factory class
     * @throws ClassNotFoundException is thrown if not found
     * @throws java.io.IOException is thrown if loading the class or META-INF file not found
     */
    Class<?> findClass(String key, String propertyPrefix, Class<?> clazz) throws ClassNotFoundException, IOException;
}
