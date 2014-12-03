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
package org.apache.camel.catalog;

import java.util.List;
import java.util.Set;

/**
 * Catalog of all the Camel components from this Apache Camel release.
 */
public interface CamelComponentCatalog {

    /**
     * Find all the component names from the Camel catalog
     */
    List<String> findComponentNames();

    /**
     * Find all the component names from the Camel catalog that matches the label
     */
    List<String> findComponentNames(String label);

    /**
     * Returns the component information as JSon format.
     *
     * @param name the component name
     * @return component details in JSon
     */
    String componentJSonSchema(String name);

    /**
     * Find all the unique label names all the components are using.
     *
     * @return a set of all the labels.
     */
    Set<String> findLabels();
}
