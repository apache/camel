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
package org.apache.camel.language.groovy;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.SimpleFunction;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The registered compile post-processors see a compiled Groovy class with an instance of it, as they see a compiled
 * Java source, so a {@code @BindToRegistry} class in a groovy file (such as a custom simple function) is bound the way
 * it is in a java file. A plain class is not instantiated.
 */
public class GroovyCompilePostProcessorTest extends CamelTestSupport {

    private final List<String> seen = new ArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        DefaultGroovyScriptCompiler compiler = new DefaultGroovyScriptCompiler();
        compiler.setCamelContext(context);
        compiler.setScriptPattern("file:src/test/resources/camel-groovy-annotated/*");
        context.addService(compiler);

        return context;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // the camel-jbang runtime registers a post-processor that binds @BindToRegistry classes
        registry.bind("myPostProcessor", (CompilePostProcessor) (camelContext, name, clazz, byteCode, instance) -> {
            seen.add(name);
            BindToRegistry bir = clazz.getAnnotation(BindToRegistry.class);
            if (bir != null) {
                camelContext.getRegistry().bind(bir.value(), instance);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody().simple("${maskEmail(${body})}")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testAnnotatedClassIsBound() throws Exception {
        assertEquals(List.of("MaskEmailFunction"), seen);
        assertInstanceOf(SimpleFunction.class, context.getRegistry().lookupByName("mask-email-function"));

        getMockEndpoint("mock:result").expectedBodiesReceived("j***@example.com");
        template.sendBody("direct:start", "john.doe@example.com");
        MockEndpoint.assertIsSatisfied(context);
    }
}
