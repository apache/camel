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
package org.apache.camel.component.bean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class BeanWithInputStreamBodyTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", new MyCoolBean());
        return jndi;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testBeanWithInputStreamBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MyCoolBean.class).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("There is 11 bytes");

        InputStream bais = new ByteArrayInputStream("Hello World".getBytes());

        template.sendBody("direct:start", bais);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanWithInputStreamBodyMethod() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MyCoolBean.class, "doSomething").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("There is 11 bytes");

        InputStream bais = new ByteArrayInputStream("Hello World".getBytes());

        template.sendBody("direct:start", bais);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToBeanWithInputStreamBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:myBean").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("There is 11 bytes");

        InputStream bais = new ByteArrayInputStream("Hello World".getBytes());

        template.sendBody("direct:start", bais);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToBeanWithInputStreamBodyMethod() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:myBean?method=doSomething").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("There is 11 bytes");

        InputStream bais = new ByteArrayInputStream("Hello World".getBytes());

        template.sendBody("direct:start", bais);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToBeanWithInputStreamBodyMethodOGNL() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:myBean?method=doSomething(${body})").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("There is 11 bytes");

        InputStream bais = new ByteArrayInputStream("Hello World".getBytes());

        template.sendBody("direct:start", bais);

        assertMockEndpointsSatisfied();
    }

    public static final class MyCoolBean {

        public static String doSomething(InputStream is) throws IOException {
            int byteCount = 0;
            while ((is.read()) != -1) {
                byteCount++;
            }
            return "There is " + byteCount + " bytes";
        }

    }
}
