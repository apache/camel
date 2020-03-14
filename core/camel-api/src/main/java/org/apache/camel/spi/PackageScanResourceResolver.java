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
import java.util.Set;

import org.apache.camel.StaticService;

/**
 * A resolver that can find resources based on package scanning.
 *
 * OSGi is not supported as this is intended for standalone Camel runtimes such
 * as Camel Main, or Camel Quarkus.
 *
 * @see PackageScanClassResolver
 */
public interface PackageScanResourceResolver extends StaticService {

    /**
     * Gets the ClassLoader instances that should be used when scanning for resources.
     * <p/>
     * This implementation will return a new unmodifiable set containing the classloaders.
     * Use the {@link #addClassLoader(ClassLoader)} method if you want to add new classloaders
     * to the class loaders list.
     *
     * @return the class loaders to use
     */
    Set<ClassLoader> getClassLoaders();

    /**
     * Adds the class loader to the existing loaders
     *
     * @param classLoader the loader to add
     */
    void addClassLoader(ClassLoader classLoader);

    /**
     * To specify a set of accepted schemas to use for loading resources as URL connections
     * (besides http and https schemas)
     */
    void setAcceptableSchemes(String schemes);

    /**
     * Finds the resources from the given location.
     *
     * The location can be prefixed with either file: or classpath: to look in either file system or classpath.
     * By default classpath is assumed if no prefix is specified.
     *
     * Wildcards is supported using a ANT pattern style paths, such as classpath:&#42;&#42;/&#42;camel&#42;.xml
     *
     * Notice when using wildcards, then there is additional overhead as the classpath is scanned, where
     * as if you specific the exact name for each XML file is faster as no classpath scanning is needed.
     *
     * <b>Important:</b> Make sure to close the returned input streams after use.
     *
     * @param location  the location (support ANT style patterns, eg routes/camel-*.xml)
     * @return the found resources, or an empty set if no resources found
     * @throws Exception can be thrown during scanning for resources.
     */
    Set<InputStream> findResources(String location) throws Exception;

    /**
     * Finds the resource names from the given location.
     *
     * The location can be prefixed with either file: or classpath: to look in either file system or classpath.
     * By default classpath is assumed if no prefix is specified.
     *
     * Wildcards is supported using a ANT pattern style paths, such as classpath:&#42;&#42;/&#42;camel&#42;.xml
     *
     * Notice when using wildcards, then there is additional overhead as the classpath is scanned, where
     * as if you specific the exact name for each XML file is faster as no classpath scanning is needed.
     *
     * @param location  the location (support ANT style patterns, eg routes/camel-*.xml)
     * @return the found resource names, or an empty set if no resources found
     * @throws Exception can be thrown during scanning for resources.
     */
    Set<String> findResourceNames(String location) throws Exception;

}
