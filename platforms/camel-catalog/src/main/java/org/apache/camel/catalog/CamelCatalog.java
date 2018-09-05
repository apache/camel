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
 * Catalog of components, data formats, models (EIPs), languages, and more from this Apache Camel release.
 */
@MXBean
public interface CamelCatalog {

    /**
     * Returns the {@link JSonSchemaResolver} used by this catalog.
     *
     * @return the resolver
     */
    JSonSchemaResolver getJSonSchemaResolver();

    /**
     * To use a custom {@link JSonSchemaResolver} with this catalog.
     *
     * @param resolver  the custom resolver
     */
    void setJSonSchemaResolver(JSonSchemaResolver resolver);

    /**
     * To plugin a custom {@link RuntimeProvider} that amends the catalog to only include information that is supported on the runtime.
     */
    void setRuntimeProvider(RuntimeProvider provider);

    /**
     * Gets the {@link RuntimeProvider} in use.
     */
    RuntimeProvider getRuntimeProvider();

    /**
     * Enables caching of the resources which makes the catalog faster, but keeps data in memory during caching.
     * <p/>
     * The catalog does not cache by default.
     */
    void enableCache();

    /**
     * Whether caching has been enabled.
     */
    boolean isCaching();

    /**
     * To plugin a custom {@link SuggestionStrategy} to provide suggestion for unknown options
     */
    void setSuggestionStrategy(SuggestionStrategy suggestionStrategy);

    /**
     * Gets the {@link SuggestionStrategy} in use
     */
    SuggestionStrategy getSuggestionStrategy();

    /**
     * To plugin a custom {@link VersionManager} to load other versions of Camel the catalog should use.
     */
    void setVersionManager(VersionManager versionManager);

    /**
     * Gets the {@link VersionManager} in use
     */
    VersionManager getVersionManager();

    /**
     * Adds a 3rd party component to this catalog.
     *
     * @param name      the component name
     * @param className the fully qualified class name for the component class
     */
    void addComponent(String name, String className);

    /**
     * Adds a 3rd party component to this catalog.
     *
     * @param name       the component name
     * @param className  the fully qualified class name for the component class
     * @param jsonSchema the component JSon schema
     */
    void addComponent(String name, String className, String jsonSchema);

    /**
     * Adds a 3rd party data format to this catalog.
     *
     * @param name      the data format name
     * @param className the fully qualified class name for the data format class
     */
    void addDataFormat(String name, String className);

    /**
     * Adds a 3rd party data format to this catalog.
     *
     * @param name      the data format name
     * @param className the fully qualified class name for the data format class
     * @param jsonSchema the data format JSon schema
     */
    void addDataFormat(String name, String className, String jsonSchema);

    /**
     * The version of this Camel Catalog
     */
    String getCatalogVersion();

    /**
     * Attempt to load the Camel version to be used by the catalog.
     * <p/>
     * Loading the camel-catalog JAR of the given version of choice may require internet access
     * to download the JAR from Maven central. You can pre download the JAR and install in a local
     * Maven repository to avoid internet access for offline environments.
     * <p/>
     * When loading a new version the cache will be invalidated.
     * <p/>
     * <b>Important:</b> When loading a new runtime provider version, then its strongly advised to
     * load the same/corresponding version first using {@link #loadVersion(String)}.
     *
     * @param version  the Camel version such as <tt>2.17.1</tt>
     * @return <tt>true</tt> if the version was loaded, <tt>false</tt> if not.
     */
    boolean loadVersion(String version);

    /**
     * Gets the current loaded Camel version used by the catalog.
     */
    String getLoadedVersion();

    /**
     * Gets the current loaded runtime provider version used by the catalog.
     */
    String getRuntimeProviderLoadedVersion();

    /**
     * Attempt to load the runtime provider version to be used by the catalog.
     * <p/>
     * Loading the runtime provider JAR of the given version of choice may require internet access
     * to download the JAR from Maven central. You can pre download the JAR and install in a local
     * Maven repository to avoid internet access for offline environments.
     * <p/>
     * <b>Important:</b> When loading a new runtime provider version, then its strongly advised to
     * load the same/corresponding version first using {@link #loadVersion(String)}.
     *
     * @param groupId  the runtime provider Maven groupId
     * @param artifactId  the runtime provider Maven artifactId
     * @param version  the runtime provider Maven version
     * @return <tt>true</tt> if the version was loaded, <tt>false</tt> if not.
     */
    boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version);

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
     * Find all the other (miscellaneous) names from the Camel catalog
     */
    List<String> findOtherNames();

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
     * Find all the other (miscellaneous) names from the Camel catalog that matches the label
     */
    List<String> findOtherNames(String filter);

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
     * Returns the other (miscellaneous) information as JSon format.
     *
     * @param name the other (miscellaneous) name
     * @return other (miscellaneous) details in JSon
     */
    String otherJSonSchema(String name);

    /**
     * Returns the model information as JSon format.
     *
     * @param name the model name
     * @return model details in JSon
     */
    String modelJSonSchema(String name);

    /**
     * Returns the component documentation as Ascii doc format.
     *
     * @param name the component name
     * @return component documentation in ascii doc format.
     */
    String componentAsciiDoc(String name);

    /**
     * Returns the component documentation as HTML format.
     *
     * @param name the component name
     * @return component documentation in html format.
     */
    String componentHtmlDoc(String name);

    /**
     * Returns the data format documentation as Ascii doc format.
     *
     * @param name the data format name
     * @return data format documentation in ascii doc format.
     */
    String dataFormatAsciiDoc(String name);

    /**
     * Returns the data format documentation as HTML format.
     *
     * @param name the data format name
     * @return data format documentation in HTML format.
     */
    String dataFormatHtmlDoc(String name);

    /**
     * Returns the language documentation as Ascii doc format.
     *
     * @param name the language name
     * @return language documentation in ascii doc format.
     */
    String languageAsciiDoc(String name);

    /**
     * Returns the language documentation as HTML format.
     *
     * @param name the language name
     * @return language documentation in HTML format.
     */
    String languageHtmlDoc(String name);

    /**
     * Returns the other (miscellaneous) documentation as Ascii doc format.
     *
     * @param name the other (miscellaneous) name
     * @return other (miscellaneous) documentation in ascii doc format.
     */
    String otherAsciiDoc(String name);

    /**
     * Returns the other (miscellaneous) documentation as HTML format.
     *
     * @param name the other (miscellaneous) name
     * @return other (miscellaneous) documentation in HTML format.
     */
    String otherHtmlDoc(String name);

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
     * Find all the unique label names all the languages are using.
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
     * Find all the unique label names all the other (miscellaneous) are using.
     *
     * @return a set of all the labels.
     */
    Set<String> findOtherLabels();

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
     * Parses the endpoint uri and constructs a key/value properties of only the lenient properties (eg custom options)
     * <p/>
     * For example using the HTTP components to provide query parameters in the endpoint uri.
     *
     * @param uri  the endpoint uri
     * @return properties as key value pairs of each lenient properties
     */
    Map<String, String> endpointLenientProperties(String uri) throws URISyntaxException;

    /**
     * Validates the pattern whether its a valid time pattern.
     *
     * @param pattern  the pattern such as 5000, 5s, 5sec, 4min, 4m30s, 1h, etc.
     * @return <tt>true</tt> if valid, <tt>false</tt> if invalid
     */
    boolean validateTimePattern(String pattern);

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
     * Parses and validates the endpoint uri and constructs a key/value properties of each option.
     * <p/>
     * The option ignoreLenientProperties can be used to ignore components that uses lenient properties.
     * When this is true, then the uri validation is stricter but would fail on properties that are not part of the component
     * but in the uri because of using lenient properties.
     * For example using the HTTP components to provide query parameters in the endpoint uri.
     *
     * @param uri  the endpoint uri
     * @param ignoreLenientProperties  whether to ignore components that uses lenient properties.
     * @param consumerOnly whether the endpoint is only used as a consumer
     * @param producerOnly whether the endpoint is only used as a producer
     * @return validation result
     */
    EndpointValidationResult validateEndpointProperties(String uri, boolean ignoreLenientProperties, boolean consumerOnly, boolean producerOnly);

    /**
     * Parses and validates the simple expression.
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> on the classpath
     *
     * @param simple  the simple expression
     * @return validation result
     * @deprecated use {@link #validateSimpleExpression(ClassLoader, String)}
     */
    @Deprecated
    SimpleValidationResult validateSimpleExpression(String simple);

    /**
     * Parses and validates the simple expression.
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> on the classpath
     *
     * @param classLoader a custom classloader to use for loading the language from the classpath, or <tt>null</tt> for using default classloader
     * @param simple  the simple expression
     * @return validation result
     */
    SimpleValidationResult validateSimpleExpression(ClassLoader classLoader, String simple);

    /**
     * Parses and validates the simple predicate
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> on the classpath
     *
     * @param simple  the simple predicate
     * @return validation result
     * @deprecated use {@link #validateSimplePredicate(ClassLoader, String)}
     */
    @Deprecated
    SimpleValidationResult validateSimplePredicate(String simple);

    /**
     * Parses and validates the simple predicate
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> on the classpath
     *
     * @param classLoader a custom classloader to use for loading the language from the classpath, or <tt>null</tt> for using default classloader
     * @param simple  the simple predicate
     * @return validation result
     */
    SimpleValidationResult validateSimplePredicate(ClassLoader classLoader, String simple);

    /**
     * Parses and validates the language as a predicate
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> and the language dependencies on the classpath
     *
     * @param classLoader a custom classloader to use for loading the language from the classpath, or <tt>null</tt> for using default classloader
     * @param language the name of the language
     * @param text  the predicate text
     * @return validation result
     */
    LanguageValidationResult validateLanguagePredicate(ClassLoader classLoader, String language, String text);

    /**
     * Parses and validates the language as an expression
     * <p/>
     * <b>Important:</b> This requires having <tt>camel-core</tt> and the language dependencies on the classpath
     *
     * @param classLoader a custom classloader to use for loading the language from the classpath, or <tt>null</tt> for using default classloader
     * @param language the name of the language
     * @param text  the expression text
     * @return validation result
     */
    LanguageValidationResult validateLanguageExpression(ClassLoader classLoader, String language, String text);

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
    @Deprecated
    String asEndpointUri(String scheme, String json, boolean encode) throws URISyntaxException;

    /**
     * Creates an endpoint uri in XML style from the information in the json schema
     *
     * @param scheme the endpoint schema
     * @param json the json schema with the endpoint properties
     * @param encode whether to URL encode the returned uri or not
     * @return the constructed endpoint uri
     * @throws java.net.URISyntaxException is thrown if there is encoding error
     */
    @Deprecated
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
     * Creates an endpoint uri in XML style from the information from the properties
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
     * Lists all the others (miscellaneous) summary details in JSon
     */
    String listOthersAsJson();

    /**
     * Reports a summary what the catalog contains in JSon
     */
    String summaryAsJson();

}
