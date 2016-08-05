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
package org.apache.camel.builder;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.JndiRegistry;

public class ErrorHandlerBuilderRefTest extends ContextTestSupport {
    ErrorHandlerBuilderRef errorHandlerBuilderRef = new ErrorHandlerBuilderRef("ref");
    
    @Override
    public boolean isUseRouteBuilder() {
        return true;
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("ref", new DefaultErrorHandlerBuilder());
        return registry;
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setErrorHandlerBuilder(errorHandlerBuilderRef);
        return context;
    }
    
    public void testErrorHandlerBuilderRef() throws Exception {
        String uuid = UUID.randomUUID().toString();
        context.addRoutes(new TempRouteBuilder(uuid));
        checkObjectSize(2);
        context.stopRoute(uuid);
        context.removeRoute(uuid);
        checkObjectSize(1);
    }
    
    private void checkObjectSize(int size) throws Exception {
        assertEquals("Get a wrong size of Route", size, context.getRoutes().size());
        Field field = ErrorHandlerBuilderRef.class.getDeclaredField("handlers");
        field.setAccessible(true);
        assertEquals("Get a wrong size of ErrorHandler", size, ((Map<?, ?>) field.get(errorHandlerBuilderRef)).size());
    }
    
    private static class TempRouteBuilder extends RouteBuilder {

        final String routeId;

        TempRouteBuilder(String routeId) {
            this.routeId = routeId;
        }

        @Override
        public void configure() throws Exception {
            from("direct:foo").routeId(routeId).to("mock:foo");
        }
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

}
