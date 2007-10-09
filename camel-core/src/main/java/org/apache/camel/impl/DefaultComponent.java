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
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * @version $Revision$
 */
public abstract class DefaultComponent<E extends Exchange> extends ServiceSupport implements Component<E> {

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
        //endcode uri sting to the unsafe URI characters        
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
        Map parameters = URISupport.parseParamters(u);

        Endpoint<E> endpoint = createEndpoint(uri, path, parameters);
        if (endpoint == null) {
            return null;
        }
        if (parameters != null) {
            if (endpoint instanceof ScheduledPollEndpoint) {
                ScheduledPollEndpoint scheduledPollEndpoint = (ScheduledPollEndpoint)endpoint;
                scheduledPollEndpoint.configureProperties(parameters);
            }
            setProperties(endpoint, parameters);
        }
        return endpoint;
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
}
