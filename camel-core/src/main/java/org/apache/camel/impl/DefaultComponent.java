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
package org.apache.camel.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.extension.ComponentExtension;
import org.apache.camel.component.extension.ComponentExtensionHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Default component to use for base for components implementations.
 */
public abstract class DefaultComponent extends ServiceSupport implements Component {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultComponent.class);
    private static final Pattern RAW_PATTERN = Pattern.compile("RAW(.*&&.*)");

    private final List<Supplier<ComponentExtension>> extensions = new ArrayList<>();

    private CamelContext camelContext;

    @Metadata(label = "advanced", defaultValue = "true",
        description = "Whether the component should resolve property placeholders on itself when starting. Only properties which are of String type can use property placeholders.")
    private boolean resolvePropertyPlaceholders = true;

    public DefaultComponent() {
    }

    public DefaultComponent(CamelContext context) {
        this.camelContext = context;
    }

    @Deprecated
    protected String preProcessUri(String uri) {
        return UnsafeUriCharactersEncoder.encode(uri);
    }

    public Endpoint createEndpoint(String uri) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
        // check URI string to the unsafe URI characters
        String encodedUri = preProcessUri(uri);
        URI u = new URI(encodedUri);
        String path;
        if (u.getScheme() != null) {
            // if there is a scheme then there is also a path
            path = URISupport.extractRemainderPath(u, useRawUri());
        } else {
            // this uri has no context-path as the leading text is the component name (scheme)
            path = null;
        }

        Map<String, Object> parameters;
        if (useRawUri()) {
            // when using raw uri then the query is taking from the uri as is
            String query;
            int idx = uri.indexOf('?');
            if (idx > -1) {
                query = uri.substring(idx + 1);
            } else {
                query = u.getRawQuery();
            }
            // and use method parseQuery
            parameters = URISupport.parseQuery(query, true);
        } else {
            // however when using the encoded (default mode) uri then the query,
            // is taken from the URI (ensures values is URI encoded)
            // and use method parseParameters
            parameters = URISupport.parseParameters(u);
        }
        // parameters using raw syntax: RAW(value)
        // should have the token removed, so its only the value we have in parameters, as we are about to create
        // an endpoint and want to have the parameter values without the RAW tokens
        URISupport.resolveRawParameterValues(parameters);

        // use encoded or raw uri?
        uri = useRawUri() ? uri : encodedUri;

        validateURI(uri, path, parameters);
        if (LOG.isTraceEnabled()) {
            // at trace level its okay to have parameters logged, that may contain passwords
            LOG.trace("Creating endpoint uri=[{}], path=[{}], parameters=[{}]", URISupport.sanitizeUri(uri), URISupport.sanitizePath(path), parameters);
        } else if (LOG.isDebugEnabled()) {
            // but at debug level only output sanitized uris
            LOG.debug("Creating endpoint uri=[{}], path=[{}]", new Object[]{URISupport.sanitizeUri(uri), URISupport.sanitizePath(path)});
        }
        Endpoint endpoint = createEndpoint(uri, path, parameters);
        if (endpoint == null) {
            return null;
        }

        endpoint.configureProperties(parameters);
        if (useIntrospectionOnEndpoint()) {
            setProperties(endpoint, parameters);
        }

        // if endpoint is strict (not lenient) and we have unknown parameters configured then
        // fail if there are parameters that could not be set, then they are probably misspell or not supported at all
        if (!endpoint.isLenientProperties()) {
            validateParameters(uri, parameters, null);
        }

        afterConfiguration(uri, path, endpoint, parameters);
        return endpoint;
    }

    @Override
    public ComponentConfiguration createComponentConfiguration() {
        return new DefaultComponentConfiguration(this);
    }

    @Override
    public EndpointConfiguration createConfiguration(String uri) throws Exception {
        MappedEndpointConfiguration config = new MappedEndpointConfiguration(getCamelContext());
        config.setURI(new URI(uri));
        return config;
    }

    @Override
    public boolean useRawUri() {
        // should use encoded uri by default
        return false;
    }

    /**
     * Whether the component should resolve property placeholders on itself when starting.
     * Only properties which are of String type can use property placeholders.
     */
    public void setResolvePropertyPlaceholders(boolean resolvePropertyPlaceholders) {
        this.resolvePropertyPlaceholders = resolvePropertyPlaceholders;
    }

    /**
     * Whether the component should resolve property placeholders on itself when starting.
     * Only properties which are of String type can use property placeholders.
     */
    public boolean isResolvePropertyPlaceholders() {
        return resolvePropertyPlaceholders;
    }

    /**
     * Strategy to do post configuration logic.
     * <p/>
     * Can be used to construct an URI based on the remaining parameters. For example the parameters that configures
     * the endpoint have been removed from the parameters which leaves only the additional parameters left.
     *
     * @param uri the uri
     * @param remaining the remaining part of the URI without the query parameters or component prefix
     * @param endpoint the created endpoint
     * @param parameters the remaining parameters after the endpoint has been created and parsed the parameters
     * @throws Exception can be thrown to indicate error creating the endpoint
     */
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        // noop
    }

    /**
     * Strategy for validation of parameters, that was not able to be resolved to any endpoint options.
     *
     * @param uri          the uri
     * @param parameters   the parameters, an empty map if no parameters given
     * @param optionPrefix optional prefix to filter the parameters for validation. Use <tt>null</tt> for validate all.
     * @throws ResolveEndpointFailedException should be thrown if the URI validation failed
     */
    protected void validateParameters(String uri, Map<String, Object> parameters, String optionPrefix) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        Map<String, Object> param = parameters;
        if (optionPrefix != null) {
            param = IntrospectionSupport.extractProperties(parameters, optionPrefix);
        }

        if (param.size() > 0) {
            throw new ResolveEndpointFailedException(uri, "There are " + param.size()
                + " parameters that couldn't be set on the endpoint."
                + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                + " Unknown parameters=[" + param + "]");
        }
    }

    /**
     * Strategy for validation of the uri when creating the endpoint.
     *
     * @param uri        the uri
     * @param path       the path - part after the scheme
     * @param parameters the parameters, an empty map if no parameters given
     * @throws ResolveEndpointFailedException should be thrown if the URI validation failed
     */
    protected void validateURI(String uri, String path, Map<String, Object> parameters) {
        // check for uri containing double && markers without include by RAW
        if (uri.contains("&&")) {
            Matcher m = RAW_PATTERN.matcher(uri);
            // we should skip the RAW part
            if (!m.find()) {
                throw new ResolveEndpointFailedException(uri, "Invalid uri syntax: Double && marker found. "
                    + "Check the uri and remove the duplicate & marker.");
            }
        }

        // if we have a trailing & then that is invalid as well
        if (uri.endsWith("&")) {
            throw new ResolveEndpointFailedException(uri, "Invalid uri syntax: Trailing & marker found. "
                + "Check the uri and remove the trailing & marker.");
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext context) {
        this.camelContext = context;
    }

    protected void doStart() throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");

        if (isResolvePropertyPlaceholders()) {
            // only resolve property placeholders if its in use
            Component existing = CamelContextHelper.lookupPropertiesComponent(camelContext, false);
            if (existing != null) {
                LOG.debug("Resolving property placeholders on component: {}", this);
                CamelContextHelper.resolvePropertyPlaceholders(camelContext, this);
            } else {
                LOG.debug("Cannot resolve property placeholders on component: {} as PropertiesComponent is not in use", this);
            }
        }
    }

    protected void doStop() throws Exception {
        // noop
    }

    /**
     * A factory method allowing derived components to create a new endpoint
     * from the given URI, remaining path and optional parameters
     *
     * @param uri the full URI of the endpoint
     * @param remaining the remaining part of the URI without the query
     *                parameters or component prefix
     * @param parameters the optional parameters passed in
     * @return a newly created endpoint or null if the endpoint cannot be
     *         created based on the inputs
     * @throws Exception is thrown if error creating the endpoint
     */
    protected abstract Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
        throws Exception;

    /**
     * Sets the bean properties on the given bean
     *
     * @param bean  the bean
     * @param parameters  properties to set
     */
    protected void setProperties(Object bean, Map<String, Object> parameters) throws Exception {
        setProperties(getCamelContext(), bean, parameters);
    }

    /**
     * Sets the bean properties on the given bean using the given {@link CamelContext}
     * @param camelContext  the {@link CamelContext} to use
     * @param bean  the bean
     * @param parameters  properties to set
     */
    protected void setProperties(CamelContext camelContext, Object bean, Map<String, Object> parameters) throws Exception {
        // set reference properties first as they use # syntax that fools the regular properties setter
        EndpointHelper.setReferenceProperties(camelContext, bean, parameters);
        EndpointHelper.setProperties(camelContext, bean, parameters);
    }

    /**
     * Derived classes may wish to overload this to prevent the default introspection of URI parameters
     * on the created Endpoint instance
     */
    protected boolean useIntrospectionOnEndpoint() {
        return true;
    }

    /**
     * Gets the parameter and remove it from the parameter map. This method doesn't resolve
     * reference parameters in the registry.
     *
     * @param parameters the parameters
     * @param key        the key
     * @param type       the requested type to convert the value from the parameter
     * @return the converted value parameter, <tt>null</tt> if parameter does not exists.
     * @see #resolveAndRemoveReferenceParameter(Map, String, Class)
     */
    public <T> T getAndRemoveParameter(Map<String, Object> parameters, String key, Class<T> type) {
        return getAndRemoveParameter(parameters, key, type, null);
    }

    /**
     * Gets the parameter and remove it from the parameter map. This method doesn't resolve
     * reference parameters in the registry.
     *
     * @param parameters    the parameters
     * @param key           the key
     * @param type          the requested type to convert the value from the parameter
     * @param defaultValue  use this default value if the parameter does not contain the key
     * @return the converted value parameter
     * @see #resolveAndRemoveReferenceParameter(Map, String, Class, Object)
     */
    public <T> T getAndRemoveParameter(Map<String, Object> parameters, String key, Class<T> type, T defaultValue) {
        Object value = parameters.remove(key);
        if (value == null) {
            value = defaultValue;
        }
        if (value == null) {
            return null;
        }

        return CamelContextHelper.convertTo(getCamelContext(), type, value);
    }

    /**
     * Gets the parameter and remove it from the parameter map. This method resolves
     * reference parameters in the registry as well.
     *
     * @param parameters    the parameters
     * @param key           the key
     * @param type          the requested type to convert the value from the parameter
     * @return the converted value parameter
     */
    public <T> T getAndRemoveOrResolveReferenceParameter(Map<String, Object> parameters, String key, Class<T> type) {
        return getAndRemoveOrResolveReferenceParameter(parameters, key, type, null);
    }

    /**
     * Gets the parameter and remove it from the parameter map. This method resolves
     * reference parameters in the registry as well.
     *
     * @param parameters    the parameters
     * @param key           the key
     * @param type          the requested type to convert the value from the parameter
     * @param defaultValue  use this default value if the parameter does not contain the key
     * @return the converted value parameter
     */
    public <T> T getAndRemoveOrResolveReferenceParameter(Map<String, Object> parameters, String key, Class<T> type, T defaultValue) {
        String value = getAndRemoveParameter(parameters, key, String.class);
        if (value == null) {
            return defaultValue;
        } else if (EndpointHelper.isReferenceParameter(value)) {
            return EndpointHelper.resolveReferenceParameter(getCamelContext(), value, type);
        } else {
            return getCamelContext().getTypeConverter().convertTo(type, value);
        }
    }

    /**
     * Resolves a reference parameter in the registry and removes it from the map. 
     *
     * @param <T>           type of object to lookup in the registry.
     * @param parameters    parameter map.
     * @param key           parameter map key.
     * @param type          type of object to lookup in the registry.
     * @return the referenced object or <code>null</code> if the parameter map 
     *         doesn't contain the key.
     * @throws IllegalArgumentException if a non-null reference was not found in 
     *         registry.
     */
    public <T> T resolveAndRemoveReferenceParameter(Map<String, Object> parameters, String key, Class<T> type) {
        return resolveAndRemoveReferenceParameter(parameters, key, type, null);
    }

    /**
     * Resolves a reference parameter in the registry and removes it from the map. 
     *
     * @param <T>           type of object to lookup in the registry.
     * @param parameters    parameter map.
     * @param key           parameter map key.
     * @param type          type of object to lookup in the registry.
     * @param defaultValue  default value to use if the parameter map doesn't 
     *                      contain the key.
     * @return the referenced object or the default value.
     * @throws IllegalArgumentException if referenced object was not found in 
     *         registry.
     */
    public <T> T resolveAndRemoveReferenceParameter(Map<String, Object> parameters, String key, Class<T> type, T defaultValue) {
        String value = getAndRemoveParameter(parameters, key, String.class);
        if (value == null) {
            return defaultValue;
        } else {
            return EndpointHelper.resolveReferenceParameter(getCamelContext(), value, type);
        }
    }

    /**
     * Resolves a reference list parameter in the registry and removes it from
     * the map.
     *
     * @param parameters parameter map.
     * @param key parameter map key.
     * @param elementType result list element type.
     * @return the list of referenced objects or an empty list if the parameter
     *         map doesn't contain the key.
     * @throws IllegalArgumentException if any of the referenced objects was
     *         not found in registry.
     * @see EndpointHelper#resolveReferenceListParameter(CamelContext, String, Class)
     */
    public <T> List<T> resolveAndRemoveReferenceListParameter(Map<String, Object> parameters, String key, Class<T> elementType) {
        return resolveAndRemoveReferenceListParameter(parameters, key, elementType, new ArrayList<>(0));
    }

    /**
     * Resolves a reference list parameter in the registry and removes it from
     * the map.
     *
     * @param parameters parameter map.
     * @param key parameter map key.
     * @param elementType result list element type.
     * @param defaultValue default value to use if the parameter map doesn't
     *            contain the key.
     * @return the list of referenced objects or the default value.
     * @throws IllegalArgumentException if any of the referenced objects was 
     *         not found in registry.
     * @see EndpointHelper#resolveReferenceListParameter(CamelContext, String, Class)
     */
    public <T> List<T> resolveAndRemoveReferenceListParameter(Map<String, Object> parameters, String key, Class<T> elementType, List<T> defaultValue) {
        String value = getAndRemoveParameter(parameters, key, String.class);

        if (value == null) {
            return defaultValue;
        } else {
            return EndpointHelper.resolveReferenceListParameter(getCamelContext(), value, elementType);
        }
    }

    /**
     * Returns the reminder of the text if it starts with the prefix.
     * <p/>
     * Is useable for string parameters that contains commands.
     *
     * @param prefix  the prefix
     * @param text  the text
     * @return the reminder, or null if no reminder
     */
    protected String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (remainder.length() > 0) {
                return remainder;
            }
        }
        return null;
    }

    protected void registerExtension(ComponentExtension extension) {
        extensions.add(() -> extension);
    }

    protected void registerExtension(Supplier<ComponentExtension> supplier) {
        extensions.add(Suppliers.memorize(supplier));
    }

    @Override
    public Collection<Class<? extends ComponentExtension>> getSupportedExtensions() {
        return extensions.stream()
            .map(Supplier::get)
            .map(ComponentExtension::getClass)
            .collect(Collectors.toList());
    }

    @Override
    public <T extends ComponentExtension> Optional<T> getExtension(Class<T> extensionType) {
        return extensions.stream()
            .map(Supplier::get)
            .filter(extensionType::isInstance)
            .findFirst()
            .map(extensionType::cast)
            .map(e -> ComponentExtensionHelper.trySetComponent(e, this))
            .map(e -> ComponentExtensionHelper.trySetCamelContext(e, getCamelContext()));
    }
}
