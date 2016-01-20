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

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MXBean;

/**
 * Catalog of components, data formats, models (EIPs), languages, and more  from this Apache Camel release.
 */
@MXBean
public interface CamelCatalog {

    /**
     * Enables caching of the resources which makes the catalog faster, but keeps data in memory during caching.
     * <p/>
     * The catalog does not cache by default.
     */
    void enableCache();

    /**
     * To plugin a custom {@link SuggestionStrategy} to provide suggestion for unknown options
     */
    void setSuggestionStrategy(SuggestionStrategy suggestionStrategy);

    /**
     * Adds a 3rd party component to this catalog.
     *
     * @param name      the component name
     * @param className the fully qualified class name for the component class
     */
    void addComponent(String name, String className);

    /**
     * Adds a 3rd party data format to this catalog.
     *
     * @param name      the data format name
     * @param className the fully qualified class name for the data format class
     */
    void addDataFormat(String name, String className);

    /**
     * The version of this Camel Catalog
     */
    String getCatalogVersion();

    /**
     * Find all the component names from the Camel catalog
     */
    List<String> findComponentNames();

    /**
     * Find all the data format names from the Camel catalog
     */
    List<String> findDataFormatNames();

    /**
     * Find all the language names from the Camel catalog
     */
    List<String> findLanguageNames();

    /**
     * Find all the model names from the Camel catalog
     */
    List<String> findModelNames();

    /**
     * Find all the component names from the Camel catalog that matches the label
     */
    List<String> findComponentNames(String filter);

    /**
     * Find all the data format names from the Camel catalog that matches the label
     */
    List<String> findDataFormatNames(String filter);

    /**
     * Find all the language names from the Camel catalog that matches the label
     */
    List<String> findLanguageNames(String filter);

    /**
     * Find all the model names from the Camel catalog that matches the label
     */
    List<String> findModelNames(String filter);

    /**
     * Returns the component information as JSon format.
     *
     * @param name the component name
     * @return component details in JSon
     */
    String componentJSonSchema(String name);

    /**
     * Returns the data format information as JSon format.
     *
     * @param name the data format name
     * @return data format details in JSon
     */
    String dataFormatJSonSchema(String name);

    /**
     * Returns the language information as JSon format.
     *
     * @param name the language name
     * @return language details in JSon
     */
    String languageJSonSchema(String name);

    /**
     * Returns the model information as JSon format.
     *
     * @param name the model name
     * @return model details in JSon
     */
    String modelJSonSchema(String name);

    /**
     * Find all the unique label names all the components are using.
     *
     * @return a set of all the labels.
     */
    Set<String> findComponentLabels();

    /**
     * Find all the unique label names all the data formats are using.
     *
     * @return a set of all the labels.
     */
    Set<String> findDataFormatLabels();

    /**
     * Find all the unique label names all the data formats are using.
     *
     * @return a set of all the labels.
     */
    Set<String> findLanguageLabels();

    /**
     * Find all the unique label names all the models are using.
     *
     * @return a set of all the labels.
     */
    Set<String> findModelLabels();

    /**
     * Returns the Apache Camel Maven Archetype catalog in XML format.
     *
     * @return the catalog in XML
     */
    String archetypeCatalogAsXml();

    /**
     * Returns the Camel Spring XML schema
     *
     * @return the spring XML schema
     */
    String springSchemaAsXml();

    /**
     * Returns the Camel Blueprint XML schema
     *
     * @return the blueprint XML schema
     */
    String blueprintSchemaAsXml();

    /**
     * Parses the endpoint uri and constructs a key/value properties of each option
     *
     * @param uri  the endpoint uri
     * @return properties as key value pairs of each endpoint option
     */
    Map<String, String> endpointProperties(String uri) throws URISyntaxException;

    /**
     * Parses and validates the endpoint uri and constructs a key/value properties of each option.
     *
     * @param uri  the endpoint uri
     * @return validation result
     */
    EndpointValidationResult validateEndpointProperties(String uri);

    /**
     * Parses and validates the endpoint uri and constructs a key/value properties of each option.
     * <p/>
     * The option ignoreLenientProperties can be used to ignore components that uses lenient properties.
     * When this is true, then the uri validation is stricter but would fail on properties that are not part of the component
     * but in the uri because of using lenient properties.
     * For example using the HTTP components to provide query parameters in the endpoint uri.
     *
     * @param uri  the endpoint uri
     * @param ignoreLenientProperties  whether to ignore components that uses lenient properties.
     * @return validation result
     */
    EndpointValidationResult validateEndpointProperties(String uri, boolean ignoreLenientProperties);

    /**
     * Parses and validates the simple expression.
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> on the classpath
     *
     * @param simple  the simple expression
     * @return validation result
     */
    SimpleValidationResult validateSimpleExpression(String simple);

    /**
     * Parses and validates the simple predicate
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> on the classpath
     *
     * @param simple  the simple predicate
     * @return validation result
     */
    SimpleValidationResult validateSimplePredicate(String simple);

    /**
     * Returns the component name from the given endpoint uri
     *
     * @param uri  the endpoint uri
     * @return the component name (aka scheme), or <tt>null</tt> if not possible to determine
     */
    String endpointComponentName(String uri);

    /**
     * Creates an endpoint uri in Java style from the information in the json schema
     *
     * @param scheme the endpoint schema
     * @param json the json schema with the endpoint properties
     * @param encode whether to URL encode the returned uri or not
     * @return the constructed endpoint uri
     * @throws java.net.URISyntaxException is thrown if there is encoding error
     */
    String asEndpointUri(String scheme, String json, boolean encode) throws URISyntaxException;

    /**
     * Creates an endpoint uri in XML style (eg escape & as &ampl;) from the information in the json schema
     *
     * @param scheme the endpoint schema
     * @param json the json schema with the endpoint properties
     * @param encode whether to URL encode the returned uri or not
     * @return the constructed endpoint uri
     * @throws java.net.URISyntaxException is thrown if there is encoding error
     */
    String asEndpointUriXml(String scheme, String json, boolean encode) throws URISyntaxException;

    /**
     * Creates an endpoint uri in Java style from the information from the properties
     *
     * @param scheme the endpoint schema
     * @param properties the properties as key value pairs
     * @param encode whether to URL encode the returned uri or not
     * @return the constructed endpoint uri
     * @throws java.net.URISyntaxException is thrown if there is encoding error
     */
    String asEndpointUri(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException;

    /**
     * Creates an endpoint uri in XML style (eg escape & as &ampl;) from the information from the properties
     *
     * @param scheme the endpoint schema
     * @param properties the properties as key value pairs
     * @param encode whether to URL encode the returned uri or not
     * @return the constructed endpoint uri
     * @throws java.net.URISyntaxException is thrown if there is encoding error
     */
    String asEndpointUriXml(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException;

    /**
     * Lists all the components summary details in JSon
     */
    String listComponentsAsJson();

    /**
     * Lists all the data formats summary details in JSon
     */
    String listDataFormatsAsJson();

    /**
     * Lists all the languages summary details in JSon
     */
    String listLanguagesAsJson();

    /**
     * Lists all the models (EIPs) summary details in JSon
     */
    String listModelsAsJson();

    /**
     * Reports a summary what the catalog contains in JSon
     */
    String summaryAsJson();

}
