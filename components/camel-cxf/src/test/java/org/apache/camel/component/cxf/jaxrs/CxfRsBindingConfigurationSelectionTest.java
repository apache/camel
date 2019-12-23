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
package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Method;
import java.util.Map;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Tests different binding configuration options of the CXFRS consumer. 
 */
public class CxfRsBindingConfigurationSelectionTest extends CamelTestSupport {
    
    private static final String RESOURCE_CLASS = "resourceClasses=org.apache.camel.component.cxf.jaxrs.simplebinding.testbean.CustomerServiceResource";
    private static final String CXF_RS_ENDPOINT_URI_CUSTOM = String.format("cxfrs://http://localhost:%s/CxfRsConsumerTest/rest?bindingStyle=Custom&", CXFTestSupport.getPort2()) 
            + RESOURCE_CLASS + "&binding=#binding";
    private static final String CXF_RS_ENDPOINT_URI_SIMPLE = String.format("cxfrs://http://localhost:%s/CxfRsConsumerTest/rest?bindingStyle=SimpleConsumer&", CXFTestSupport.getPort1()) 
            + RESOURCE_CLASS;
    private static final String CXF_RS_ENDPOINT_URI_DEFAULT = String.format("cxfrs://http://localhost:%s/CxfRsConsumerTest/rest?bindingStyle=Default&", CXFTestSupport.getPort3()) + RESOURCE_CLASS;
    private static final String CXF_RS_ENDPOINT_URI_NONE = String.format("cxfrs://http://localhost:%s/CxfRsConsumerTest/rest?", CXFTestSupport.getPort4()) + RESOURCE_CLASS;
    
    @BindToRegistry("binding")
    private DummyCxfRsBindingImplementation dummyCxfRsBindingImplementation = new DummyCxfRsBindingImplementation();
    
    @Test
    public void testCxfRsBindingConfiguration() {
        // check binding styles
        assertEquals(BindingStyle.Custom, endpointForRouteId("custom").getBindingStyle());
        assertEquals(BindingStyle.SimpleConsumer, endpointForRouteId("simple").getBindingStyle());
        assertEquals(BindingStyle.Default, endpointForRouteId("default").getBindingStyle());
        assertEquals(BindingStyle.Default, endpointForRouteId("none").getBindingStyle());
        
        // check binding implementations
        assertEquals(DummyCxfRsBindingImplementation.class, endpointForRouteId("custom").getBinding().getClass());
        assertEquals(SimpleCxfRsBinding.class, endpointForRouteId("simple").getBinding().getClass());
        assertEquals(DefaultCxfRsBinding.class, endpointForRouteId("default").getBinding().getClass());
        assertEquals(DefaultCxfRsBinding.class, endpointForRouteId("default").getBinding().getClass());
    }
    
    private CxfRsEndpoint endpointForRouteId(String routeId) {
        return (CxfRsEndpoint) context.getRoute(routeId).getConsumer().getEndpoint();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                from(CXF_RS_ENDPOINT_URI_CUSTOM).routeId("custom")
                    .to("log:foo");
                
                from(CXF_RS_ENDPOINT_URI_SIMPLE).routeId("simple")
                    .to("log:foo");
                
                from(CXF_RS_ENDPOINT_URI_DEFAULT).routeId("default")
                    .to("log:foo");
                
                from(CXF_RS_ENDPOINT_URI_NONE).routeId("none")
                    .to("log:foo");
                
            }
        };
    }
    
    private final class DummyCxfRsBindingImplementation implements CxfRsBinding {
        @Override
        public void populateExchangeFromCxfRsRequest(org.apache.cxf.message.Exchange cxfExchange, Exchange camelExchange, Method method, Object[] paramArray) {
        }

        @Override
        public Object populateCxfRsResponseFromExchange(Exchange camelExchange, org.apache.cxf.message.Exchange cxfExchange) throws Exception {
            return null;
        }

        @Override
        public Object bindResponseToCamelBody(Object response, Exchange camelExchange) throws Exception {
            return null;
        }

        @Override
        public Map<String, Object> bindResponseHeadersToCamelHeaders(Object response, Exchange camelExchange) throws Exception {
            return null;
        }

        @Override
        public Entity<Object> bindCamelMessageToRequestEntity(Object body, Message camelMessage, Exchange camelExchange) {
            return null;
        }

        @Override
        public Object bindCamelMessageBodyToRequestBody(Message camelMessage, Exchange camelExchange) throws Exception {
            return null;
        }

        @Override
        public MultivaluedMap<String, String> bindCamelHeadersToRequestHeaders(Map<String, Object> camelHeaders, Exchange camelExchange) throws Exception {
            return null;
        }
    }

}
