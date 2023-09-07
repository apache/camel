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

package org.apache.camel.test.infra.core.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.util.URISupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class CamelTestSupport implements ConfigurableContext, ConfigurableRoute {

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected volatile ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @BeforeEach
    public void doSetup() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

/*    public CamelContext context() {
        return context;
    }*/

/*    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }*/

    protected MockEndpoint getMockEndpoint(String uri) {
        return getMockEndpoint(uri, true);
    }

    protected MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        String n;
        try {
            n = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }

        int idx = n.indexOf('?');
        if (idx != -1) {
            n = n.substring(0, idx);
        }
        final String target = n;

        MockEndpoint found = (MockEndpoint) context.getEndpointRegistry().values().stream()
                .filter(e -> e instanceof MockEndpoint).filter(e -> {
                    String t = e.getEndpointUri();

                    int idx2 = t.indexOf('?');
                    if (idx2 != -1) {
                        t = t.substring(0, idx2);
                    }
                    return t.equals(target);
                }).findFirst().orElse(null);

        if (found != null) {
            return found;
        }

        if (create) {
            return resolveMandatoryEndpoint(uri, MockEndpoint.class);
        } else {
            throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
        }
    }

    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return resolveMandatoryEndpoint(context, uri, endpointType);
    }
    
    public static <T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String uri, Class<T> endpointType) {
        T endpoint = context.getEndpoint(uri, endpointType);

        assertNotNull(endpoint, "No endpoint found for URI: " + uri);

        return endpoint;
    }

/*
    protected CamelContext createCamelContext() throws Exception {
        Registry registry = createCamelRegistry();

        CamelContext retContext;
        if (registry != null) {
            retContext = new DefaultCamelContext(registry);
        } else {
            retContext = new DefaultCamelContext();
        }

        return retContext;
    }
*/

/*    protected CamelContext createCamelContext(){
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.setStreamCaching(Boolean.FALSE);
        return ctx;
    }*/

/*    @ContextFixture
    public void createCamelContext() throws Exception{
        context = camelContextExtension.getContext();
    }*/




/*    protected void startCamelContext() {
        if (camelContextService != null) {
            camelContextService.start();
        } else {
            if (context instanceof DefaultCamelContext defaultCamelContext) {
                if (!defaultCamelContext.isStarted()) {
                    defaultCamelContext.start();
                }
            } else {
                context.start();
            }
        }
    }*/

/*    public Service getCamelContextService() {
        return camelContextService;
    }

    public void setCamelContextService(Service service) {
        camelContextService = service;
        THREAD_SERVICE.set(camelContextService);
    }*/


    // context.getCamelContextExtension().get;
        
        // MockEndpoint.resolve(context, uri);

    @Override
    public void configureContext(CamelContext context) throws Exception {

    }

    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {

    }

    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }
}
