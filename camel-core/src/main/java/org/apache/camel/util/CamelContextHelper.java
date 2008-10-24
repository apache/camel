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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Registry;

import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import static org.apache.camel.util.ObjectHelper.notNull;
/**
 * A number of helper methods
 *
 * @version $Revision$
 */
public final class CamelContextHelper {
    /**
     * Utility classes should not have a public constructor.
     */
    private CamelContextHelper() {
    }

    /**
     * Returns the mandatory endpoint for the given URI or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     */
    public static Endpoint getMandatoryEndpoint(CamelContext camelContext, String uri)
        throws NoSuchEndpointException {
        Endpoint endpoint = camelContext.getEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri);
        } else {
            return endpoint;
        }
    }

    public static String getEndpointKey(String uri, Endpoint ep) {
        return ep.isSingleton() ? uri : ("Ox" + Integer.toHexString(ep.hashCode()) + ":" + uri);
    }

    /**
     * Returns the mandatory endpoint for the given URI and type or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     */
    public static <T extends Endpoint> T getMandatoryEndpoint(CamelContext camelContext, String uri, Class<T> type) {
        Endpoint endpoint = getMandatoryEndpoint(camelContext, uri);
        return ObjectHelper.cast(type, endpoint);
    }

    /**
     * Returns a list of all endpoints of the given type
     *
     * @param camelContext the camel context
     * @param type the type of the endpoints requested
     * @return a list which may be empty of all the endpoint instances of the
     *         given type
     */    
    public <T> List<T> getEndpoints(CamelContext camelContext, Class<T> type) {
        return getEndpointsImpl(camelContext, type, false);
    }        
    
    /**
     * Returns a list of all singleton endpoints of the given type
     *
     * @param camelContext the camel context
     * @param type the type of the endpoints requested
     * @return a list which may be empty of all the endpoint instances of the
     *         given type
     */
    public static <T> List<T> getSingletonEndpoints(CamelContext camelContext, Class<T> type) {
        return getEndpointsImpl(camelContext, type, true);
    }

    /**
     * Returns a list of all singleton or regular endpoints of the given type
     */
    private static <T> List<T> getEndpointsImpl(CamelContext camelContext, Class<T> type, boolean singleton) {
        List<T> answer = new ArrayList<T>();
        Collection<Endpoint> endpoints = singleton ? camelContext.getSingletonEndpoints() : camelContext.getEndpoints();
        for (Endpoint endpoint : endpoints) {
            if (type.isInstance(endpoint)) {
                T value = type.cast(endpoint);
                answer.add(value);
            }
        }
        return answer;
    }          
    
    /**
     * Converts the given value to the requested type
     */
    public static <T> T convertTo(CamelContext context, Class<T> type, Object value) {
        notNull(context, "camelContext");
        return context.getTypeConverter().convertTo(type, value);
    }

    /**
     * Converts the given value to the specified type throwing an {@link IllegalArgumentException}
     * if the value could not be converted to a non null value
     */
    public static <T> T mandatoryConvertTo(CamelContext context, Class<T> type, Object value) {
        T answer = convertTo(context, type, value);
        if (answer == null) {
            throw new IllegalArgumentException("Value " + value + " converted to " + type.getName() + " cannot be null");
        }
        return answer;
    }

    /**
     * Creates a new instance of the given type using the {@link Injector} on the given
     * {@link CamelContext}
     */
    public static <T> T newInstance(CamelContext context, Class<T> beanType) {
        return context.getInjector().newInstance(beanType);
    }

    /**
     * Look up the given named bean in the {@link Registry} on the
     * {@link CamelContext}
     */
    public static Object lookup(CamelContext context, String name) {
        return context.getRegistry().lookup(name);
    }

    /**
     * Look up the given named bean of the given type in the {@link Registry} on the
     * {@link CamelContext}
     */
    public static <T> T lookup(CamelContext context, String name, Class<T> beanType) {
        return context.getRegistry().lookup(name, beanType);
    }

    /**
     * Look up the given named bean in the {@link Registry} on the
     * {@link CamelContext} or throws
     */
    public static Object mandatoryLookup(CamelContext context, String name) {
        Object answer = lookup(context, name);
        notNull(answer, "registry entry called " + name);
        return answer;
    }

    /**
     * Look up the given named bean of the given type in the {@link Registry} on the
     * {@link CamelContext}
     */
    public static <T> T mandatoryLookup(CamelContext context, String name, Class<T> beanType) {
        T answer = lookup(context, name, beanType);
        notNull(answer, "registry entry called " + name + " of type " + beanType.getName());
        return answer;
    }

    /**
     * Resolves the given language name into a {@link Language} or throws an exception if it could not be converted
     */
    public static Language resolveMandatoryLanguage(CamelContext camelContext, String languageName) {
        notNull(camelContext, "camelContext");
        notNull(languageName, "languageName");

        Language language = camelContext.resolveLanguage(languageName);
        if (language == null) {
            throw new IllegalArgumentException("Could not resolve language: " + languageName);
        }
        return language;
    }

    /**
     * Resolves the mandatory language name and expression text into a {@link Expression} instance
     * throwing an exception if it could not be created
     */
    public static Expression resolveMandatoryExpression(CamelContext camelContext, String languageName, String expressionText) {
        notNull(expressionText, "expressionText");

        Language language = resolveMandatoryLanguage(camelContext, languageName);
        Expression<Exchange> expression = language.createExpression(expressionText);
        if (expression == null) {
            throw new IllegalArgumentException("Could not create expression: " + expressionText + " with language: " + language);
        }
        return expression;
    }

    /**
     * Evaluates the @EndpointInject annotation using the given context
     */
    public static Endpoint getEndpointInjection(CamelContext camelContext, String uri, String name, String injectionPointName, boolean mandatory) {
        Endpoint endpoint = null;
        if (isNotNullAndNonEmpty(uri)) {
            endpoint = camelContext.getEndpoint(uri);
        } else {
            if (isNullOrBlank(name)) {
                name = injectionPointName;
            }
            if (mandatory) {
                endpoint = mandatoryLookup(camelContext, name, Endpoint.class);
            } else {
                endpoint = lookup(camelContext, name, Endpoint.class);
            }
        }
        return endpoint;
    }

}
