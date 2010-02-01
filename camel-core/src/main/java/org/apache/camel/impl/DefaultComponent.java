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
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Default component to use for base for components implementations.
 *
 * @version $Revision$
 */
public abstract class DefaultComponent<E extends Exchange> extends ServiceSupport implements Component<E> {
    private static final transient Log LOG = LogFactory.getLog(DefaultComponent.class);

    private int defaultThreadPoolSize = 5;
    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    
    public DefaultComponent() {
    }

    public DefaultComponent(CamelContext context) {
        this.camelContext = context;
    }

    public Endpoint<E> createEndpoint(String uri) throws Exception {
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
        Endpoint<E> endpoint = createEndpoint(uri, path, parameters);
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
            if (!endpoint.isLenientProperties() && parameters.size() > 0) {
                throw new ResolveEndpointFailedException(uri, "There are " + parameters.size()
                    + " parameters that couldn't be set on the endpoint."
                    + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                    + " Unknown parameters=[" + parameters + "]");
            }
        }

        return endpoint;
    }

    /**
     * Strategy for validation of the uri when creating the endpoint.
     *
     * @param uri        the uri - the uri the end user provided untouched
     * @param path       the path - part after the scheme
     * @param parameters the parameters, an empty map if no parameters given
     * @throws ResolveEndpointFailedException should be thrown if the URI validation failed
     */
    protected void validateURI(String uri, String path, Map parameters) throws ResolveEndpointFailedException {
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

    public ScheduledExecutorService getExecutorService() {
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
    protected ScheduledExecutorService createExecutorService() {
        return new ScheduledThreadPoolExecutor(defaultThreadPoolSize, new ThreadFactory() {
            int counter;

            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("Thread: " + (++counter) + " " + DefaultComponent.this.toString());
                return thread;
            }
        });
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
    protected abstract Endpoint<E> createEndpoint(String uri, String remaining, Map parameters)
        throws Exception;

    /**
     * Sets the bean properties on the given bean
     */
    protected void setProperties(Object bean, Map parameters) throws Exception {
        IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(), bean, parameters);
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
     * Creates a new instance of the given type using the {@link Injector} on the given
     * {@link CamelContext}
     */
    public  <T> T newInstance(Class<T> beanType) {
        return getCamelContext().getInjector().newInstance(beanType);
    }

    /**
     * Look up the given named bean in the {@link Registry} on the
     * {@link CamelContext}
     */
    public Object lookup(String name) {
        return getCamelContext().getRegistry().lookup(name);
    }

    /**
     * Look up the given named bean of the given type in the {@link Registry} on the
     * {@link CamelContext}
     */
    public <T> T lookup(String name, Class<T> beanType) {
        return getCamelContext().getRegistry().lookup(name, beanType);
    }

    /**
     * Look up the given named bean in the {@link Registry} on the
     * {@link CamelContext} or throws
     */
    public Object mandatoryLookup(String name) {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), name);
    }

    /**
     * Look up the given named bean of the given type in the {@link Registry} on the
     * {@link CamelContext}
     */
    public <T> T mandatoryLookup(String name, Class<T> beanType) {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), name, beanType);
    }

    /**
     * Gets the parameter and remove it from the parameter map.
     * 
     * @param parameters  the parameters
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
     * @param parameters     the parameters
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

}
