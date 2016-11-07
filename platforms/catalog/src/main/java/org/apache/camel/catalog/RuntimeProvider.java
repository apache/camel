/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.catalog;

import java.util.List;

/**
 * A pluggable strategy for chosen runtime to run Camel such as default, karaf, spring-boot, etc.
 * This allows third party runtimes to provide their own provider, that can amend the catalog
 * to match the runtime. For example spring-boot or karaf does not support all the default Camel components.
 */
public interface RuntimeProvider {

    // TODO: maven archetype GAV
    // original GAV
    // spring-boot GAV
    // karaf feature name

    /**
     * Gets the {@link CamelCatalog}
     */
    CamelCatalog getCamelCatalog();

    /**
     * Sets the {@link CamelCatalog} to use
     */
    void setCamelCatalog(CamelCatalog camelCatalog);

    /**
     * Name of provider such as <tt>default</tt>, <tt>karaf</tt>, <tt>spring-boot</tt>
     */
    String getProviderName();

    /**
     * Find all the component names from the Camel catalog supported by the provider
     */
    List<String> findComponentNames();

    /**
     * Find all the data format names from the Camel catalog supported by the provider
     */
    List<String> findDataFormatNames();

    /**
     * Find all the language names from the Camel catalog supported by the provider
     */
    List<String> findLanguageNames();

}
