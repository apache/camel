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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CompilePreProcessor;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registered compile pre-processors see a Groovy script before it is compiled, as they see a Java source
 * (CAMEL-24843): with the Camel CLI that is what downloads the library of an import.
 */
public class GroovyCompilePreProcessorTest extends CamelTestSupport {

    private final List<String> seen = new ArrayList<>();

    @Override
    protected void bindToRegistry(org.apache.camel.spi.Registry registry) {
        registry.bind("myPreProcessor",
                (CompilePreProcessor) (CamelContext camelContext, String name, String code) -> seen.add(code));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody().groovy("import java.util.Locale\nbody.toUpperCase(Locale.ROOT)")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testPreProcessorSeesTheScript() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HELLO");
        template.sendBody("direct:start", "hello");
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, seen.size(), seen.toString());
        assertTrue(seen.get(0).contains("import java.util.Locale"), seen.get(0));
    }

    @Test
    public void testUnresolvedClassSaysWhatToDo() {
        GroovyLanguage groovy = (GroovyLanguage) context.resolveLanguage("groovy");
        Exception e = assertThrows(Exception.class,
                () -> groovy.evaluate("import org.example.missing.EmailValidator\nEmailValidator.getInstance()", null,
                        Object.class));
        String msg = e.getMessage();
        assertTrue(msg.contains("Groovy cannot resolve the class org.example.missing.EmailValidator"), msg);
        assertTrue(msg.contains("camel.jbang.dependencies=groupId:artifactId:version"), msg);
        assertTrue(msg.contains("Maven dependency"), msg);
    }
}
