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
package org.apache.camel.component.jte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A template given in the {@link JteConstants#JTE_TEMPLATE} header is compiled once for its content, not once for every
 * exchange: each compiled template is a class that the template engine keeps, and a source and a class file in the work
 * directory.
 */
class JteTemplateFromHeaderCompiledOnceTest extends CamelTestSupport {

    private static final String WORK_DIR = "target/jte-header-compiled-once";

    private static final String HELLO = """
            @import org.apache.camel.component.jte.Model
            @param Model model

            Hello ${model.header("name")}.
            """;

    private static final String BYE = """
            @import org.apache.camel.component.jte.Model
            @param Model model

            Bye ${model.header("name")}.
            """;

    @Override
    protected void doPreSetup() throws Exception {
        FileUtil.removeDir(Path.of(WORK_DIR).toFile());
    }

    @Test
    void sameTemplateIsCompiledOnce() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertEquals("Hello Scott" + i + ".", render(HELLO, "Scott" + i));
        }

        assertEquals(1, compiledTemplates(), "The template of the 20 exchanges should have been compiled once");
    }

    @Test
    void eachDistinctTemplateIsCompiledOnce() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals("Hello Scott" + i + ".", render(HELLO, "Scott" + i));
            assertEquals("Bye Scott" + i + ".", render(BYE, "Scott" + i));
        }

        assertEquals(2, compiledTemplates(), "Each of the two templates should have been compiled once");
    }

    private String render(String content, String name) throws Exception {
        Exchange exchange = template.request("direct:a", e -> {
            e.getIn().setHeader(JteConstants.JTE_TEMPLATE, content);
            e.getIn().setHeader("name", name);
        });
        if (exchange.isFailed()) {
            throw exchange.getException();
        }
        return exchange.getMessage().getBody(String.class);
    }

    private static long compiledTemplates() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(WORK_DIR))) {
            return files.filter(f -> f.getFileName().toString().endsWith(".class")).count();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                JteComponent jte = context.getComponent("jte", JteComponent.class);
                jte.setWorkDir(WORK_DIR);

                from("direct:a").to("jte:org/apache/camel/component/jte/dummy.jte?allowTemplateFromHeader=true");
            }
        };
    }
}
