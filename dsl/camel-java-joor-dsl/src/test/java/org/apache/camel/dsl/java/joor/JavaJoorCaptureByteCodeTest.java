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
package org.apache.camel.dsl.java.joor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaJoorCaptureByteCodeTest {

    private final MyPostCompiler postCompiler = new MyPostCompiler();

    @Test
    public void testCaptureByteCode() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("MyPostCompiler", postCompiler);
            context.start();

            Resource resource = context.getResourceLoader().resolveResource("/routes/MyMockRoute.java");
            Collection<RoutesBuilder> builders = context.getRoutesLoader().findRoutesBuilders(resource);

            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            builder.configure();

            assertEquals("MyMockRoute", postCompiler.getName());
            assertNotNull(postCompiler.getCode());
        }

        // load the route (its byte code) from another context

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource res = new ResourceSupport("class", "MyMockRoute.class") {
                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(postCompiler.getCode());
                }
            };

            Collection<RoutesBuilder> builders = context.getRoutesLoader().findRoutesBuilders(res);
            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            context.addRoutes(builder);

            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedBodiesReceived("Hello ByteCode");
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("direct:start", "Hello ByteCode");
            mock.assertIsSatisfied();
        }
    }

    private static final class MyPostCompiler implements CompilePostProcessor {

        private String name;
        private byte[] code;

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            this.name = name;
            this.code = byteCode;
        }

        public String getName() {
            return name;
        }

        public byte[] getCode() {
            return code;
        }
    }
}
