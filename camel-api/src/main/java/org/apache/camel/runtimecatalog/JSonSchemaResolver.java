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
package org.apache.camel.runtimecatalog;

/**
 * Pluggable resolver to load JSon schema files for components, data formats, languages etc.
 */
public interface JSonSchemaResolver {

    /**
     * Returns the component information as JSon format.
     *
     * @param name the component name
     * @return component details in JSon
     */
    String getComponentJSonSchema(String name);

    /**
     * Returns the data format information as JSon format.
     *
     * @param name the data format name
     * @return data format details in JSon
     */
    String getDataFormatJSonSchema(String name);

    /**
     * Returns the language information as JSon format.
     *
     * @param name the language name
     * @return language details in JSon
     */
    String getLanguageJSonSchema(String name);

    /**
     * Returns the other (miscellaneous) information as JSon format.
     *
     * @param name the other (miscellaneous) name
     * @return other (miscellaneous) details in JSon
     */
    String getOtherJSonSchema(String name);

    /**
     * Returns the model information as JSon format.
     *
     * @param name the model name
     * @return model details in JSon
     */
    String getModelJSonSchema(String name);

}
