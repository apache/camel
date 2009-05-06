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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Default component to use for base for components implementations.
 *
 * @version $Revision$
 */
public abstract class DefaultComponent extends ServiceSupport implements Component {
    private static final transient Log LOG = LogFactory.getLog(DefaultComponent.class);

    private static final int DEFAULT_THREADPOOL_SIZE = 5;
    private CamelContext camelContext;
    private ExecutorService executorService;

    public DefaultComponent() {
    }

    public DefaultComponent(CamelContext context) {
        this.camelContext = context;
    }

    public Endpoint createEndpoint(String uri) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
        //encode URI string to the unsafe URI characters
        URI u = new URI(UnsafeUriCharactersEncoder.encode(uri));
        String path = u.getSchemeSpecificPart();

        // lets trim off any query arguments
        if (path.startsWith("//")) {
            path = path.substring(2);
        }
        int idx = path.indexOf('?');
        if (idx > 0) {
            path = path.substring(0, idx);
        }
        Map parameters = URISupport.parseParameters(u);

        validateURI(uri, path, parameters);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating endpoint uri=[" + uri + "], path=[" + path + "], parameters=[" + parameters + "]");
        }
        Endpoint endpoint = createEndpoint(uri, path, parameters);
        if (endpoint == null) {
            return null;
        }

        if (parameters != null) {
            endpoint.configureProperties(parameters);
            if (useIntrospectionOnEndpoint()) {
                setProperties(endpoint, parameters);
            }

            // if endpoint is strict (not lenient) and we have unknown parameters configured then
            // fail if there are parameters that could not be set, then they are probably miss spelt or not supported at all
            if (!endpoint.isLenientProperties()) {
                validateParameters(uri, parameters, null);
            }
        }

        return endpoint;
    }

    /**
     * Strategy for validation of parameters, that was not able to be resolved to any endpoint options.
     *
     * @param uri          the uri - the uri the end user provided untouched
     * @param parameters   the parameters, an empty map if no parameters given
     * @param optionPrefix optional prefix to filter the parameters for validation. Use <tt>null</tt> for validate all.
     * @throws ResolveEndpointFailedException should be thrown if the URI validation failed
     */
    protected void validateParameters(String uri, Map parameters, String optionPrefix) {
        Map param = parameters;
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
     * @param uri        the uri - the uri the end user provided untouched
     * @param path       the path - part after the scheme
     * @param parameters the parameters, an empty map if no parameters given
     * @throws ResolveEndpointFailedException should be thrown if the URI validation failed
     */
    protected void validateURI(String uri, String path, Map parameters) {
        // check for uri containing & but no ? marker
        if (uri.contains("&") && !uri.contains("?")) {
            throw new ResolveEndpointFailedException(uri, "Invalid uri syntax: no ? marker however the uri "
                + "has & parameter separators. Check the uri if its missing a ? marker.");
        }

        // check for uri containing double && markers
        if (uri.contains("&&")) {
            throw new ResolveEndpointFailedException(uri, "Invalid uri syntax: Double && marker found. "
                + "Check the uri and remove the duplicate & marker.");
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext context) {
        this.camelContext = context;
    }

    public ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = createExecutorService();
        }
        return executorService;
    }

    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }
    
    /**
     * A factory method to create a default thread pool and executor
     */
    protected ExecutorService createExecutorService() {
        return ExecutorServiceHelper.newScheduledThreadPool(DEFAULT_THREADPOOL_SIZE, this.toString(), true);
    }

    protected void doStart() throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
    }

    protected void doStop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
        }
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
     */
    protected abstract Endpoint createEndpoint(String uri, String remaining, Map parameters)
        throws Exception;

    /**
     * Sets the bean properties on the given bean
     *
     * @param bean  the bean
     * @param parameters  properties to set
     */
    protected void setProperties(Object bean, Map parameters) throws Exception {        
        // set reference properties first as they use # syntax that fools the regular properties setter
        setReferenceProperties(bean, parameters);
        IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(), bean, parameters);      
    }

    /**
     * Sets the reference properties on the given bean
     * <p/>
     * This is convention over configuration, setting all reference parameters (using {@link #isReferenceParameter(String)}
     * by looking it up in registry and setting it on the bean if possible.
     */
    protected void setReferenceProperties(Object bean, Map parameters) throws Exception {
        Iterator it = parameters.keySet().iterator();
        while (it.hasNext()) {
            Object key = it.next();
            String value = (String) parameters.get(key);
            if (isReferenceParameter(value)) {
                Object ref = lookup(value.substring(1));
                String name = key.toString();
                if (ref != null) {
                    boolean hit = IntrospectionSupport.setProperty(getCamelContext().getTypeConverter(), bean, name, ref);
                    if (hit) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Configued property: " + name + " on bean: " + bean + " with value: " + ref);
                        }
                        // must remove as its a valid option and we could configure it
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Is the given parameter a reference parameter (starting with a # char)
     */
    protected boolean isReferenceParameter(String parameter) {
        return parameter != null && parameter.startsWith("#");
    }

    /**
     * Derived classes may wish to overload this to prevent the default introspection of URI parameters
     * on the created Endpoint instance
     */
    protected boolean useIntrospectionOnEndpoint() {
        return true;
    }


    // Some helper methods
    //-------------------------------------------------------------------------

    /**
     * Converts the given value to the requested type
     */
    public <T> T convertTo(Class<T> type, Object value) {
        return CamelContextHelper.convertTo(getCamelContext(), type, value);
    }

    /**
     * Converts the given value to the specified type throwing an {@link IllegalArgumentException}
     * if the value could not be converted to a non null value
     */
    public  <T> T mandatoryConvertTo(Class<T> type, Object value) {
        return CamelContextHelper.mandatoryConvertTo(getCamelContext(), type, value);
    }

    /**
     * Creates a new instance of the given type using the {@link org.apache.camel.spi.Injector} on the given
     * {@link CamelContext}
     */
    public  <T> T newInstance(Class<T> beanType) {
        return getCamelContext().getInjector().newInstance(beanType);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext}
     */
    public Object lookup(String name) {
        return getCamelContext().getRegistry().lookup(name);
    }

    /**
     * Look up the given named bean of the given type in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext}
     */
    public <T> T lookup(String name, Class<T> beanType) {
        return getCamelContext().getRegistry().lookup(name, beanType);
    }

    /**
     * Look up the given named bean in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext} or throws exception if not found.
     */
    public Object mandatoryLookup(String name) {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), name);
    }

    /**
     * Look up the given named bean of the given type in the {@link org.apache.camel.spi.Registry} on the
     * {@link CamelContext} or throws exception if not found.
     */
    public <T> T mandatoryLookup(String name, Class<T> beanType) {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), name, beanType);
    }

    /**
     * Gets the parameter and remove it from the parameter map.
     * 
     * @param parameters the parameters
     * @param key        the key
     * @param type       the requested type to convert the value from the parameter
     * @return  the converted value parameter, <tt>null</tt> if parameter does not exists.
     */
    public <T> T getAndRemoveParameter(Map parameters, String key, Class<T> type) {
        return getAndRemoveParameter(parameters, key, type, null);
    }

    /**
     * Gets the parameter and remove it from the parameter map.
     *
     * @param parameters    the parameters
     * @param key           the key
     * @param type          the requested type to convert the value from the parameter
     * @param defaultValue  use this default value if the parameter does not contain the key
     * @return  the converted value parameter
     */
    public <T> T getAndRemoveParameter(Map parameters, String key, Class<T> type, T defaultValue) {
        Object value = parameters.remove(key);
        if (value == null) {
            value = defaultValue;
        }
        if (value == null) {
            return null;
        }
        return convertTo(type, value);
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

}
