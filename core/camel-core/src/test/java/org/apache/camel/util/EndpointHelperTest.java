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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.EndpointHelper;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

public class EndpointHelperTest extends ContextTestSupport {

    private Endpoint foo;
    private Endpoint bar;

    @Test
    public void testPollEndpoint() throws Exception {
        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:foo", "Bye World");

        final List<String> bodies = new ArrayList<>();
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

    @Test
    public void testPollEndpointTimeout() throws Exception {
        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:foo", "Bye World");

        final List<String> bodies = new ArrayList<>();
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
        CamelContext context = super.createCamelContext();
        foo = context.getEndpoint("mock:foo");
        bar = context.getEndpoint("mock:bar");

        context.getRegistry().bind("foo", foo);
        context.getRegistry().bind("coolbar", bar);
        context.getRegistry().bind("numbar", "12345");

        return context;
    }

    @Test
    public void testLookupEndpointRegistryId() throws Exception {
        assertEquals("foo", EndpointHelper.lookupEndpointRegistryId(foo));
        assertEquals("coolbar", EndpointHelper.lookupEndpointRegistryId(bar));
        assertNull(EndpointHelper.lookupEndpointRegistryId(context.getEndpoint("mock:cheese")));
    }

    @Test
    public void testLookupEndpointRegistryIdUsingRef() throws Exception {
        foo = context.getEndpoint("ref:foo");
        bar = context.getEndpoint("ref:coolbar");

        assertEquals("foo", EndpointHelper.lookupEndpointRegistryId(foo));
        assertEquals("coolbar", EndpointHelper.lookupEndpointRegistryId(bar));
        assertNull(EndpointHelper.lookupEndpointRegistryId(context.getEndpoint("mock:cheese")));
    }

    @Test
    public void testResolveReferenceParameter() throws Exception {
        Endpoint endpoint = EndpointHelper.resolveReferenceParameter(context, "coolbar", Endpoint.class);
        assertNotNull(endpoint);
        assertSame(bar, endpoint);
    }

    @Test
    public void testResolveAndConvertReferenceParameter() throws Exception {
        // The registry value is a java.lang.String
        Integer number = EndpointHelper.resolveReferenceParameter(context, "numbar", Integer.class);
        assertNotNull(number);
        assertEquals(12345, (int) number);
    }

    @Test
    public void testResolveAndConvertMissingReferenceParameter() throws Exception {
        Integer number = EndpointHelper.resolveReferenceParameter(context, "misbar", Integer.class, false);
        assertNull(number);
    }

    @Test
    public void testMandatoryResolveAndConvertMissingReferenceParameter() throws Exception {
        try {
            EndpointHelper.resolveReferenceParameter(context, "misbar", Integer.class, true);
            fail();
        } catch (NoSuchBeanException ex) {
            assertEquals("No bean could be found in the registry for: misbar of type: java.lang.Integer", ex.getMessage());
        }
    }

    @Test
    public void testResolveParameter() throws Exception {
        Endpoint endpoint = EndpointHelper.resolveParameter(context, "#coolbar", Endpoint.class);
        assertNotNull(endpoint);
        assertSame(bar, endpoint);

        Integer num = EndpointHelper.resolveParameter(context, "123", Integer.class);
        assertNotNull(num);
        assertEquals(123, num.intValue());
    }

    @Test
    public void matchEndpointsShouldIgnoreQueryParamOrder() {
        String endpointUri = "sjms:queue:my-queue?transacted=true&consumerCount=1";
        String endpointUriShuffled = "sjms:queue:my-queue?consumerCount=1&transacted=true";
        String notMatchingEndpointUri = "sjms:queue:my-queue?consumerCount=1";

        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUri, endpointUri), is(true));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUri, endpointUriShuffled), is(true));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUriShuffled, endpointUri), is(true));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUriShuffled, endpointUriShuffled), is(true));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, endpointUriShuffled), is(false));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, endpointUri), is(false));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUri, notMatchingEndpointUri), is(false));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUriShuffled, notMatchingEndpointUri), is(false));
    }

    @Test
    public void matchEndpointsShouldMatchWildcards() {
        String endpointUri = "sjms:queue:my-queue?transacted=true&consumerCount=1";
        String notMatchingEndpointUri = "sjms:queue:my-queue";
        String pattern = "sjms:queue:my-queue?*";

        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUri, pattern), is(true));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, pattern), is(false));
    }

    @Test
    public void matchEndpointsShouldMatchRegex() {
        String endpointUri = "sjms:queue:my-queue?transacted=true&consumerCount=1";
        String notMatchingEndpointUri = "sjms:queue:my-queue?transacted=false&consumerCount=1";
        String pattern = "sjms://.*transacted=true.*";

        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, endpointUri, pattern), is(true));
        MatcherAssert.assertThat(EndpointHelper.matchEndpoint(null, notMatchingEndpointUri, pattern), is(false));
    }

    @Test
    public void testResolveByType() throws Exception {
        AuthorizationPolicy myPolicy = new AuthorizationPolicy() {
            @Override
            public void beforeWrap(Route route, NamedNode definition) {
                // noop
            }

            @Override
            public Processor wrap(Route route, Processor processor) {
                return processor;
            }
        };

        context.getRegistry().bind("foobar", myPolicy);

        AuthorizationPolicy policy = EndpointHelper.resolveReferenceParameter(context,
                "#type:org.apache.camel.spi.AuthorizationPolicy", AuthorizationPolicy.class);
        assertNotNull(policy);
        assertSame(myPolicy, policy);
    }

    @Test
    public void testResolveByTypeNoBean() throws Exception {
        try {
            EndpointHelper.resolveReferenceParameter(context, "#type:org.apache.camel.spi.AuthorizationPolicy",
                    AuthorizationPolicy.class);
            fail("Should throw exception");
        } catch (NoSuchBeanException e) {
            // expected
        }
    }

    @Test
    public void testResolveByTypeTwo() throws Exception {
        AuthorizationPolicy myPolicy = new AuthorizationPolicy() {
            @Override
            public void beforeWrap(Route route, NamedNode definition) {
                // noop
            }

            @Override
            public Processor wrap(Route route, Processor processor) {
                return processor;
            }
        };
        context.getRegistry().bind("foobar", myPolicy);

        AuthorizationPolicy myPolicy2 = new AuthorizationPolicy() {
            @Override
            public void beforeWrap(Route route, NamedNode definition) {
                // noop
            }

            @Override
            public Processor wrap(Route route, Processor processor) {
                return processor;
            }
        };
        context.getRegistry().bind("foobar2", myPolicy2);

        // when there are 2 instances of the same time, then we cannot decide
        try {
            EndpointHelper.resolveReferenceParameter(context, "#type:org.apache.camel.spi.AuthorizationPolicy",
                    AuthorizationPolicy.class);
            fail("Should throw exception");
        } catch (NoSuchBeanException e) {
            // expected
        }
    }

}
