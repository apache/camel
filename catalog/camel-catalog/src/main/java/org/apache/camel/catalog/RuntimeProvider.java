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
package org.apache.camel.catalog;

import java.util.List;

/**
 * A pluggable strategy for chosen runtime to run Camel such as default, karaf, spring-boot, etc. This allows third
 * party runtimes to provide their own provider, that can amend the catalog to match the runtime. For example
 * spring-boot or karaf does not support all the default Camel components.
 */
public interface RuntimeProvider {

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
     * Maven group id of the runtime provider JAR dependency.
     */
    String getProviderGroupId();

    /**
     * Maven artifact id of the runtime provider JAR dependency.
     */
    String getProviderArtifactId();

    /**
     * Gets the directory where the component json files are stored in the catalog JAR file
     */
    String getComponentJSonSchemaDirectory();

    /**
     * Gets the directory where the data format json files are stored in the catalog JAR file
     */
    String getDataFormatJSonSchemaDirectory();

    /**
     * Gets the directory where the language json files are stored in the catalog JAR file
     */
    String getLanguageJSonSchemaDirectory();

    /**
     * Gets the directory where the transformer json files are stored in the catalog JAR file
     */
    String getTransformerJSonSchemaDirectory();

    /**
     * Gets the directory where the other (miscellaneous) json files are stored in the catalog JAR file
     */
    String getOtherJSonSchemaDirectory();

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

    /**
     * Find all the transfromer names from the Camel catalog supported by the provider
     */
    List<String> findTransformerNames();

    /**
     * Find all the other (miscellaneous) names from the Camel catalog supported by the provider
     */
    List<String> findOtherNames();

}
