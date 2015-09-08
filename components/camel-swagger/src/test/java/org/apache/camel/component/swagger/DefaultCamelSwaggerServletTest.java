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
package org.apache.camel.component.swagger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import scala.collection.immutable.List;
import scala.collection.mutable.Buffer;

public class DefaultCamelSwaggerServletTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/hello")
                    .get("/hi").to("log:hi")
                    .get("/bye").to("log:bye")
                    .post("/bye").to("log:bye");
            }
        };
    }

    @Test
    public void testServlet() throws Exception {
        DefaultCamelSwaggerServlet servlet = new DefaultCamelSwaggerServlet();
        
        Buffer<RestDefinition> list = servlet.getRestDefinitions(null);
        assertEquals(1, list.size());
        RestDefinition rest = list.iterator().next();
        checkRestDefinition(rest);

        // get the RestDefinition by using the camel context id
        list = servlet.getRestDefinitions(context.getName());
        assertEquals(1, list.size());
        rest = list.iterator().next();
        checkRestDefinition(rest);
        
        RestDefinition rest2 = context.getRestDefinitions().get(0);
        checkRestDefinition(rest2);
    }
    
    @Test
    public void testContexts() throws Exception {
        DefaultCamelSwaggerServlet servlet = new DefaultCamelSwaggerServlet();

        List<String> list = servlet.findCamelContexts();
        assertEquals(1, list.length());
        assertEquals(context.getName(), list.head());
    }

    private void checkRestDefinition(RestDefinition rest) {
        assertNotNull(rest);
        assertEquals("/hello", rest.getPath());
        assertEquals("/hi", rest.getVerbs().get(0).getUri());
        assertEquals("get", rest.getVerbs().get(0).asVerb());
        assertEquals("/bye", rest.getVerbs().get(1).getUri());
        assertEquals("get", rest.getVerbs().get(1).asVerb());
        assertEquals("/bye", rest.getVerbs().get(2).getUri());
        assertEquals("post", rest.getVerbs().get(2).asVerb());
    }

}
