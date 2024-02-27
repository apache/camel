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
package org.apache.camel.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * A number of helper methods
 */
public final class CamelContextHelper {

    public static final String MODEL_DOCUMENTATION_PREFIX = "META-INF/org/apache/camel/model/";

    /**
     * Utility classes should not have a public constructor.
     */
    private CamelContextHelper() {
    }

    /**
     * Returns the mandatory endpoint for the given URI or the {@link org.apache.camel.NoSuchEndpointException} is
     * thrown
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

    /**
     * Returns the mandatory endpoint for the given URI or the {@link org.apache.camel.NoSuchEndpointException} is
     * thrown
     */
    public static Endpoint getMandatoryEndpoint(CamelContext camelContext, NormalizedEndpointUri uri)
            throws NoSuchEndpointException {
        Endpoint endpoint = camelContext.getCamelContextExtension().getEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri.getUri());
        } else {
            return endpoint;
        }
    }

    /**
     * Returns the mandatory endpoint (prototype scope) for the given URI or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     */
    public static Endpoint getMandatoryPrototypeEndpoint(CamelContext camelContext, String uri)
            throws NoSuchEndpointException {
        Endpoint endpoint = camelContext.getCamelContextExtension().getPrototypeEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri);
        } else {
            return endpoint;
        }
    }

    /**
     * Returns the mandatory endpoint (prototype scope) for the given URI or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     */
    public static Endpoint getMandatoryPrototypeEndpoint(CamelContext camelContext, NormalizedEndpointUri uri)
            throws NoSuchEndpointException {
        Endpoint endpoint = camelContext.getCamelContextExtension().getPrototypeEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri.getUri());
        } else {
            return endpoint;
        }
    }

    /**
     * Returns the mandatory endpoint for the given URI and type or the {@link org.apache.camel.NoSuchEndpointException}
     * is thrown
     */
    public static <T extends Endpoint> T getMandatoryEndpoint(CamelContext camelContext, String uri, Class<T> type) {
        Endpoint endpoint = getMandatoryEndpoint(camelContext, uri);
        return ObjectHelper.cast(type, endpoint);
    }

    public static Endpoint resolveEndpoint(CamelContext camelContext, String uri, String ref) {
        Endpoint endpoint = null;
        if (uri != null) {
            endpoint = camelContext.getEndpoint(uri);
            if (endpoint == null) {
                throw new NoSuchEndpointException(uri);
            }
        }
        if (ref != null) {
            endpoint = camelContext.getRegistry().lookupByNameAndType(ref, Endpoint.class);
            if (endpoint == null) {
                throw new NoSuchEndpointException("ref:" + ref, "check your camel registry with id " + ref);
            }
            // Check the endpoint has the right CamelContext
            if (!camelContext.equals(endpoint.getCamelContext())) {
                throw new NoSuchEndpointException(
                        "ref:" + ref, "make sure the endpoint has the same camel context as the route does.");
            }
            try {
                // need add the endpoint into service
                camelContext.addService(endpoint);
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("Either 'uri' or 'ref' must be specified");
        } else {
            return endpoint;
        }
    }

    /**
     * Converts the given value to the requested type
     */
    public static <T> T convertTo(CamelContext context, Class<T> type, Object value) {
        return context.getTypeConverter().convertTo(type, value);
    }

    /**
     * Tried to convert the given value to the requested type
     */
    public static <T> T tryConvertTo(CamelContext context, Class<T> type, Object value) {
        return context.getTypeConverter().tryConvertTo(type, value);
    }

    /**
     * Converts the given value to the specified type throwing an {@link IllegalArgumentException} if the value could
     * not be converted to a non null value
     */
    public static <T> T mandatoryConvertTo(CamelContext context, Class<T> type, Object value) {
        T answer = convertTo(context, type, value);
        if (answer == null) {
            throw new IllegalArgumentException("Value " + value + " converted to " + type.getName() + " cannot be null");
        }
        return answer;
    }

    /**
     * Creates a new instance of the given type using the {@link org.apache.camel.spi.Injector} on the given
     * {@link CamelContext}
     */
    public static <T> T newInstance(CamelContext context, Class<T> beanType) {
        return context.getInjector().newInstance(beanType);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the {@link CamelContext}
     */
    public static Object lookup(CamelContext context, String name) {
        return context.getRegistry().lookupByName(name);
    }

    /**
     * Look up the given named bean of the given type in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext}
     */
    public static <T> T lookup(CamelContext context, String name, Class<T> beanType) {
        return context.getRegistry().lookupByNameAndType(name, beanType);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the {@link CamelContext} and try to
     * convert it to the given type.
     */
    public static <T> T lookupAndConvert(CamelContext context, String name, Class<T> beanType) {
        return tryConvertTo(context, beanType, lookup(context, name));
    }

    /**
     * Look up a bean of the give type in the {@link org.apache.camel.spi.Registry} on the {@link CamelContext}
     * returning an instance if only one bean is present,
     */
    public static <T> T findSingleByType(CamelContext camelContext, Class<T> type) {
        return camelContext.getRegistry().findSingleByType(type);
    }

    /**
     * Look up a bean of the give type in the {@link org.apache.camel.spi.Registry} on the {@link CamelContext} or
     * throws {@link org.apache.camel.NoSuchBeanTypeException} if not a single bean was found.
     */
    public static <T> T mandatoryFindSingleByType(CamelContext camelContext, Class<T> type) {
        return camelContext.getRegistry().mandatoryFindSingleByType(type);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the {@link CamelContext} or throws
     * {@link NoSuchBeanException} if not found.
     */
    public static Object mandatoryLookup(CamelContext context, String name) {
        Object answer = lookup(context, name);
        if (answer == null) {
            throw new NoSuchBeanException(name);
        }
        return answer;
    }

    /**
     * Look up the given named bean of the given type in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext} or throws NoSuchBeanException if not found.
     */
    public static <T> T mandatoryLookup(CamelContext context, String name, Class<T> beanType) {
        T answer = lookup(context, name, beanType);
        if (answer == null) {
            throw new NoSuchBeanException(name, beanType.getName());
        }
        return answer;
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the {@link CamelContext} and convert
     * it to the given type or throws NoSuchBeanException if not found.
     */
    public static <T> T mandatoryLookupAndConvert(CamelContext context, String name, Class<T> beanType) {
        Object value = lookup(context, name);
        if (value == null) {
            throw new NoSuchBeanException(name, beanType.getName());
        }
        return convertTo(context, beanType, value);
    }

    /**
     * Evaluates the @EndpointInject annotation using the given context
     */
    public static Endpoint getEndpointInjection(
            CamelContext camelContext, String uri, String injectionPointName, boolean mandatory) {
        Endpoint endpoint;
        if (isNotEmpty(uri)) {
            endpoint = camelContext.getEndpoint(uri);
        } else {
            if (mandatory) {
                endpoint = mandatoryLookup(camelContext, injectionPointName, Endpoint.class);
            } else {
                endpoint = lookup(camelContext, injectionPointName, Endpoint.class);
            }
        }
        return endpoint;
    }

    /**
     * Gets the maximum cache pool size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}. If no property
     * has been set, then it will fallback to return a size of 1000.
     *
     * @param  camelContext             the camel context
     * @return                          the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumCachePoolSize(CamelContext camelContext) throws IllegalArgumentException {
        return getPositiveIntegerProperty(camelContext, Exchange.MAXIMUM_CACHE_POOL_SIZE);
    }

    /**
     * Gets the maximum endpoint cache size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_ENDPOINT_CACHE_SIZE}. If no
     * property has been set, then it will fallback to return a size of 1000.
     *
     * @param  camelContext             the camel context
     * @return                          the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumEndpointCacheSize(CamelContext camelContext) throws IllegalArgumentException {
        return getPositiveIntegerProperty(camelContext, Exchange.MAXIMUM_ENDPOINT_CACHE_SIZE);
    }

    /**
     * Gets the maximum simple cache size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_SIMPLE_CACHE_SIZE}. If no property
     * has been set, then it will fallback to return a size of 1000. Use value of 0 or negative to disable the cache.
     *
     * @param  camelContext             the camel context
     * @return                          the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumSimpleCacheSize(CamelContext camelContext) throws IllegalArgumentException {
        return getPositiveIntegerProperty(camelContext, Exchange.MAXIMUM_SIMPLE_CACHE_SIZE);
    }

    /**
     * Gets the maximum transformer cache size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_TRANSFORMER_CACHE_SIZE}. If no
     * property has been set, then it will fallback to return a size of 1000.
     *
     * @param  camelContext             the camel context
     * @return                          the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumTransformerCacheSize(CamelContext camelContext) throws IllegalArgumentException {
        return getPositiveIntegerProperty(camelContext, Exchange.MAXIMUM_TRANSFORMER_CACHE_SIZE);
    }

    private static int getPositiveIntegerProperty(CamelContext camelContext, String property) {
        if (camelContext != null) {
            String s = camelContext.getGlobalOption(property);
            if (s != null) {
                // we cannot use Camel type converters as they may not be ready this early
                try {
                    int size = Integer.parseInt(s);
                    if (size <= 0) {
                        throw new IllegalArgumentException(
                                "Property " + property + " must be a positive number, was: "
                                                           + s);
                    }
                    return size;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Property " + property + " must be a positive number, was: " + s, e);
                }
            }
        }

        // 1000 is the default fallback
        return 1000;
    }

    /**
     * Gets the maximum validator cache size.
     * <p/>
     * Will use the property set on CamelContext with the key {@link Exchange#MAXIMUM_VALIDATOR_CACHE_SIZE}. If no
     * property has been set, then it will fallback to return a size of 1000.
     *
     * @param  camelContext             the camel context
     * @return                          the maximum cache size
     * @throws IllegalArgumentException is thrown if the property is illegal
     */
    public static int getMaximumValidatorCacheSize(CamelContext camelContext) throws IllegalArgumentException {
        return getPositiveIntegerProperty(camelContext, Exchange.MAXIMUM_VALIDATOR_CACHE_SIZE);
    }

    /**
     * Parses the given text and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the parsed text, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument
     */
    public static String parseText(CamelContext camelContext, String text) {
        // ensure we support property placeholders
        return camelContext.resolvePropertyPlaceholders(text);
    }

    /**
     * Parses the given text and converts it to an Integer and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the integer vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument or type conversion not possible
     */
    public static Integer parseInteger(CamelContext camelContext, String text) {
        return parse(camelContext, Integer.class, text);
    }

    /**
     * Parses the given text and converts it to an Integer and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the int value, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument or type conversion not possible
     */
    public static Integer parseInt(CamelContext camelContext, String text) {
        return parse(camelContext, Integer.class, text);
    }

    /**
     * Parses the given text and converts it to an Long and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the long value, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument or type conversion not possible
     */
    public static Long parseLong(CamelContext camelContext, String text) {
        return parse(camelContext, Long.class, text);
    }

    /**
     * Parses the given text and converts it to a Duration and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the Duration value, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument or type conversion not possible
     */
    public static Duration parseDuration(CamelContext camelContext, String text) {
        return parse(camelContext, Duration.class, text);
    }

    /**
     * Parses the given text and converts it to a Float and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the float value, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument or type conversion not possible
     */
    public static Float parseFloat(CamelContext camelContext, String text) {
        return parse(camelContext, Float.class, text);
    }

    /**
     * Parses the given text and converts it to a Double and handling property placeholders as well
     *
     * @param  camelContext          the camel context
     * @param  text                  the text
     * @return                       the double vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalStateException is thrown if illegal argument or type conversion not possible
     */
    public static Double parseDouble(CamelContext camelContext, String text) {
        return parse(camelContext, Double.class, text);
    }

    /**
     * Parses the given text and converts it to an Boolean and handling property placeholders as well
     *
     * @param  camelContext             the camel context
     * @param  text                     the text
     * @return                          the boolean vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalArgumentException is thrown if illegal argument or type conversion not possible
     */
    public static Boolean parseBoolean(CamelContext camelContext, String text) {
        return parse(camelContext, Boolean.class, text);
    }

    /**
     * Parses the given text and converts it to the specified class and handling property placeholders as well
     *
     * @param  camelContext             the camel context
     * @param  clazz                    the class to convert the value to
     * @param  text                     the text
     * @return                          the boolean vale, or <tt>null</tt> if the text was <tt>null</tt>
     * @throws IllegalArgumentException is thrown if illegal argument or type conversion not possible
     */
    public static <T> T parse(CamelContext camelContext, Class<T> clazz, String text) {
        // ensure we support property placeholders
        String s = camelContext.resolvePropertyPlaceholders(text);
        if (s != null) {
            try {
                return camelContext.getTypeConverter().mandatoryConvertTo(clazz, s);
            } catch (Exception e) {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as a " + clazz.getName() + ".", e);
                } else {
                    throw new IllegalArgumentException(
                            "Error parsing [" + s + "] from property " + text + " as a " + clazz.getName() + ".", e);
                }
            }
        }
        return null;
    }

    /**
     * Gets the route startup order for the given route id
     *
     * @param  camelContext the camel context
     * @param  routeId      the id of the route
     * @return              the startup order, or <tt>0</tt> if not possible to determine
     */
    public static int getRouteStartupOrder(CamelContext camelContext, String routeId) {
        for (RouteStartupOrder order : camelContext.getCamelContextExtension().getRouteStartupOrder()) {
            if (order.getRoute().getId().equals(routeId)) {
                return order.getStartupOrder();
            }
        }
        return 0;
    }

    /**
     * A helper method to access a camel context properties with a prefix
     *
     * @param  prefix       the prefix
     * @param  camelContext the camel context
     * @return              the properties which holds the camel context properties with the prefix, and the key omit
     *                      the prefix part
     */
    public static Properties getCamelPropertiesWithPrefix(String prefix, CamelContext camelContext) {
        Properties answer = new Properties();
        Map<String, String> camelProperties = camelContext.getGlobalOptions();
        if (camelProperties != null) {
            for (Map.Entry<String, String> entry : camelProperties.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.startsWith(prefix)) {
                    answer.put(key.substring(prefix.length()), entry.getValue());
                }
            }
        }
        return answer;
    }

    /**
     * Gets the route id the given node belongs to.
     *
     * @param  node the node
     * @return      the route id, or <tt>null</tt> if not possible to find
     */
    public static String getRouteId(NamedNode node) {
        NamedNode parent = node;
        while (parent != null && parent.getParent() != null) {
            parent = parent.getParent();
        }
        return parent != null ? parent.getId() : null;
    }

    /**
     * Gets the route the given node belongs to.
     *
     * @param  node the node
     * @return      the route, or <tt>null</tt> if not possible to find
     */
    public static NamedRoute getRoute(NamedNode node) {
        NamedNode parent = node;
        while (parent != null && parent.getParent() != null) {
            parent = parent.getParent();
        }
        if (parent instanceof NamedRoute) {
            return (NamedRoute) parent;
        }
        return null;
    }

    /**
     * Gets the {@link RestConfiguration} from the {@link CamelContext} and check if the component which consumes the
     * configuration is compatible with the one for which the rest configuration is set-up.
     *
     * @param  camelContext             the camel context
     * @param  component                the component that will consume the {@link RestConfiguration}
     * @return                          the {@link RestConfiguration}
     * @throws IllegalArgumentException is the component is not compatible with the {@link RestConfiguration} set-up
     */
    public static RestConfiguration getRestConfiguration(CamelContext camelContext, String component) {
        RestConfiguration configuration = camelContext.getRestConfiguration();

        validateRestConfigurationComponent(component, configuration.getComponent());

        return configuration;
    }

    /**
     * Gets the {@link RestConfiguration} from the {@link CamelContext} and check if the component which consumes the
     * configuration is compatible with the one for which the rest configuration is set-up.
     *
     * @param  camelContext             the camel context
     * @param  component                the component that will consume the {@link RestConfiguration}
     * @param  producerComponent        the producer component that will consume the {@link RestConfiguration}
     * @return                          the {@link RestConfiguration}
     * @throws IllegalArgumentException is the component is not compatible with the {@link RestConfiguration} set-up
     */
    public static RestConfiguration getRestConfiguration(
            CamelContext camelContext, String component, String producerComponent) {
        RestConfiguration configuration = camelContext.getRestConfiguration();

        validateRestConfigurationComponent(component, configuration.getComponent());
        validateRestConfigurationComponent(producerComponent, configuration.getProducerComponent());

        return configuration;
    }

    /**
     * Gets the components from the given {@code CamelContext} that match with the given predicate.
     *
     * @param  camelContext the camel context
     * @param  predicate    the predicate to evaluate to know whether a given component should be returned or not.
     * @return              the existing components that match the predicate.
     */
    public static List<Component> getComponents(CamelContext camelContext, Predicate<Component> predicate) {
        return camelContext.getComponentNames().stream()
                .map(camelContext::getComponent)
                .filter(predicate)
                .toList();
    }

    /**
     * Gets the endpoints from the given {@code CamelContext} that match with the given predicate
     *
     * @param  camelContext the camel context
     * @param  predicate    the predicate to evaluate to know whether a given endpoint should be returned or not.
     * @return              the existing endpoints that match the predicate.
     */
    public static List<Endpoint> getEndpoints(CamelContext camelContext, Predicate<Endpoint> predicate) {
        return camelContext.getEndpoints().stream()
                .filter(predicate)
                .toList();
    }

    private static void validateRestConfigurationComponent(String component, String configurationComponent) {
        if (ObjectHelper.isEmpty(component) || ObjectHelper.isEmpty(configurationComponent)) {
            return;
        }

        if (!Objects.equals(component, configurationComponent)) {
            throw new IllegalArgumentException(
                    "No RestConfiguration for component: " + component + " found, RestConfiguration targets: "
                                               + configurationComponent);
        }
    }

}
