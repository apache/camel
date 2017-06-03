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

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.StaticService;

/**
 * Runtime based CamelCatalog which are included in camel-core that can provided limit CamelCatalog capabilities
 */
public interface RuntimeCamelCatalog extends StaticService {

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
     * Validates the properties for the given scheme against component and endpoint
     *
     * @param scheme  the endpoint scheme
     * @param properties  the endpoint properties
     * @return validation result
     */
    EndpointValidationResult validateProperties(String scheme, Map<String, String> properties);

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

}
