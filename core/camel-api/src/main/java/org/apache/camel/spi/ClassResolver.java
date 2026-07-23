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
package org.apache.camel.spi;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Platform-neutral strategy for loading classes and classpath resources across the different runtimes that Camel
 * supports (standalone JVM, Spring Boot, Quarkus, OSGi, JBang, and others).
 * <p/>
 * A single {@link org.apache.camel.CamelContext} maintains one {@code ClassResolver} instance. Instead of calling
 * {@code Class.forName()} or {@code ClassLoader.getResourceAsStream()} directly, all Camel internals delegate to this
 * interface so that custom or container-aware class loaders can participate in class resolution without changes to the
 * framework. Additional class loaders can be plugged in at any time via {@link #addClassLoader(ClassLoader)}.
 * <p/>
 * The interface exposes both optional-return ({@link #resolveClass(String)}) and throwing
 * ({@link #resolveMandatoryClass(String)}) variants so that callers can choose the appropriate error-handling strategy.
 *
 * @see PackageScanClassResolver
 * @see org.apache.camel.CamelContext#getClassResolver()
 */
public interface ClassResolver {

    /**
     * Adds a custom class loader to use.
     *
     * @param classLoader a custom class loader
     */
    void addClassLoader(ClassLoader classLoader);

    /**
     * Gets the custom class loaders.
     */
    Set<ClassLoader> getClassLoaders();

    /**
     * Gets a custom class loader by its name
     *
     * @param  name the name of the custom classloader
     * @return      the class loader or <tt>null</tt> if not found
     */
    @Nullable
    ClassLoader getClassLoader(String name);

    /**
     * Resolves the given class by its name
     *
     * @param  name full qualified name of class
     * @return      the class if resolved, <tt>null</tt> if not found.
     */
    @Nullable
    Class<?> resolveClass(String name);

    /**
     * Resolves the given class by its name
     *
     * @param  name full qualified name of class
     * @param  type the expected type of the class
     * @return      the class if resolved, <tt>null</tt> if not found.
     */
    <T> @Nullable Class<T> resolveClass(String name, Class<T> type);

    /**
     * Resolves the given class by its name
     *
     * @param  name   full qualified name of class
     * @param  loader use the provided class loader
     * @return        the class if resolved, <tt>null</tt> if not found.
     */
    @Nullable
    Class<?> resolveClass(String name, ClassLoader loader);

    /**
     * Resolves the given class by its name
     *
     * @param  name   full qualified name of class
     * @param  type   the expected type of the class
     * @param  loader use the provided class loader
     * @return        the class if resolved, <tt>null</tt> if not found.
     */
    <T> @Nullable Class<T> resolveClass(String name, Class<T> type, ClassLoader loader);

    /**
     * Resolves the given class by its name
     *
     * @param  name                   full qualified name of class
     * @return                        the class if resolved, <tt>null</tt> if not found.
     * @throws ClassNotFoundException is thrown if class not found
     */
    Class<?> resolveMandatoryClass(String name) throws ClassNotFoundException;

    /**
     * Resolves the given class by its name
     *
     * @param  name                   full qualified name of class
     * @param  type                   the expected type of the class
     * @return                        the class if resolved, <tt>null</tt> if not found.
     * @throws ClassNotFoundException is thrown if class not found
     */
    <T> Class<T> resolveMandatoryClass(String name, Class<T> type) throws ClassNotFoundException;

    /**
     * Resolves the given class by its name
     *
     * @param  name                   full qualified name of class
     * @param  loader                 use the provided class loader
     * @return                        the class if resolved, <tt>null</tt> if not found.
     * @throws ClassNotFoundException is thrown if class not found
     */
    Class<?> resolveMandatoryClass(String name, ClassLoader loader) throws ClassNotFoundException;

    /**
     * Resolves the given class by its name
     *
     * @param  name                   full qualified name of class
     * @param  type                   the expected type of the class
     * @param  loader                 use the provided class loader
     * @return                        the class if resolved, <tt>null</tt> if not found.
     * @throws ClassNotFoundException is thrown if class not found
     */
    <T> Class<T> resolveMandatoryClass(String name, Class<T> type, ClassLoader loader) throws ClassNotFoundException;

    /**
     * Loads the given resource as a stream
     *
     * @param  uri the uri of the resource
     * @return     as a stream
     */
    @Nullable
    InputStream loadResourceAsStream(String uri);

    /**
     * Loads the given resource as a URL
     *
     * @param  uri the uri of the resource
     * @return     as a URL
     */
    @Nullable
    URL loadResourceAsURL(String uri);

    /**
     * Loads the given resources as a URL from the current bundle/classloader
     *
     * @param  uri the uri of the resource
     * @return     the URLs found on the classpath
     */
    Enumeration<URL> loadResourcesAsURL(String uri);

    /**
     * Loads the given resources as a URL from all bundles/classloaders
     *
     * @param  uri the uri of the resource
     * @return     the URLs found on the classpath
     */
    Enumeration<URL> loadAllResourcesAsURL(String uri);
}
