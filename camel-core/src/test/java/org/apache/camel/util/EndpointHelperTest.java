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
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

/**
 * @version 
 */
public class EndpointHelperTest extends ContextTestSupport {

    private Endpoint foo;
    private Endpoint bar;

    public void testPollEndpoint() throws Exception {
        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:foo", "Bye World");

        final List<String> bodies = new ArrayList<String>();
        // uses 1 sec default timeout
        EndpointHelper.pollEndpoint(context.getEndpoint("seda:foo"), new Processor() {
            public void process(Exchange exchange) throws Exception {
                bodies.add(exchange.getIn().getBody(String.class));
            }
        });

        assertEquals(2, bodies.size());
        assertEquals("Hello World", bodies.get(0));
        assertEquals("Bye World", bodies.get(1));
    }

    public void testPollEndpointTimeout() throws Exception {
        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:foo", "Bye World");

        final List<String> bodies = new ArrayList<String>();
        EndpointHelper.pollEndpoint(context.getEndpoint("seda:foo"), new Processor() {
            public void process(Exchange exchange) throws Exception {
                bodies.add(exchange.getIn().getBody(String.class));
            }
        }, 10);

        assertEquals(2, bodies.size());
        assertEquals("Hello World", bodies.get(0));
        assertEquals("Bye World", bodies.get(1));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry reg = new SimpleRegistry();
        CamelContext context = new DefaultCamelContext(reg);

        foo = context.getEndpoint("mock:foo");
        bar = context.getEndpoint("mock:bar");
        reg.put("foo", foo);
        reg.put("coolbar", bar);
        reg.put("numbar", "12345");

        return context;
    }

    public void testLookupEndpointRegistryId() throws Exception {
        assertEquals("foo", EndpointHelper.lookupEndpointRegistryId(foo));
        assertEquals("coolbar", EndpointHelper.lookupEndpointRegistryId(bar));
        assertEquals(null, EndpointHelper.lookupEndpointRegistryId(context.getEndpoint("mock:cheese")));
    }

    public void testLookupEndpointRegistryIdUsingRef() throws Exception {
        foo = context.getEndpoint("ref:foo");
        bar = context.getEndpoint("ref:coolbar");

        assertEquals("foo", EndpointHelper.lookupEndpointRegistryId(foo));
        assertEquals("coolbar", EndpointHelper.lookupEndpointRegistryId(bar));
        assertEquals(null, EndpointHelper.lookupEndpointRegistryId(context.getEndpoint("mock:cheese")));
    }

    public void testResolveReferenceParameter() throws Exception {
        Endpoint endpoint = EndpointHelper.resolveReferenceParameter(context, "coolbar", Endpoint.class);
        assertNotNull(endpoint);
        assertSame(bar, endpoint);
    }

    public void testResolveAndConvertReferenceParameter() throws Exception {
        // The registry value is a java.lang.String
        Integer number = EndpointHelper.resolveReferenceParameter(context, "numbar", Integer.class);
        assertNotNull(number);
        assertEquals(12345, (int) number);
    }

    public void testResolveAndConvertMissingReferenceParameter() throws Exception {
        Integer number = EndpointHelper.resolveReferenceParameter(context, "misbar", Integer.class, false);
        assertNull(number);
    }

    public void testMandatoryResolveAndConvertMissingReferenceParameter() throws Exception {
        try {
            EndpointHelper.resolveReferenceParameter(context, "misbar", Integer.class, true);
            fail();
        } catch (NoSuchBeanException ex) {
            assertEquals("No bean could be found in the registry for: misbar of type: java.lang.Integer", ex.getMessage());
        }
    }

    public void testResolveParameter() throws Exception {
        Endpoint endpoint = EndpointHelper.resolveParameter(context, "#coolbar", Endpoint.class);
        assertNotNull(endpoint);
        assertSame(bar, endpoint);

        Integer num = EndpointHelper.resolveParameter(context, "123", Integer.class);
        assertNotNull(num);
        assertEquals(123, num.intValue());
    }

}
