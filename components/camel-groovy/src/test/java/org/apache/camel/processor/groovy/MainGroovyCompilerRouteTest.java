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

package org.apache.camel.processor.groovy;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.groovy.DefaultGroovyScriptCompiler;
import org.apache.camel.main.Main;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainGroovyCompilerRouteTest {

    @Test
    public void testCompilerRoute() throws Exception {
        FileUtil.removeDir(new File("target/workdir"));

        Main main = new Main();
        main.configure().addRoutesBuilder(createRouteBuilder());
        main.configure().withCompileWorkDir("target/workdir");
        main.start();

        CamelContext context = main.getCamelContext();

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("I want to order 2 gauda");

        context.createProducerTemplate().sendBodyAndHeader("direct:start", "Hello World", "amount", 2);

        MockEndpoint.assertIsSatisfied(context);

        DefaultGroovyScriptCompiler compiler = context.hasService(DefaultGroovyScriptCompiler.class);
        Assertions.assertNotNull(compiler);
        Assertions.assertEquals(
                "classpath:camel-groovy/*,classpath:camel-groovy-compiled/*", compiler.getScriptPattern());
        Assertions.assertEquals(2, compiler.getClassesSize());
        Assertions.assertEquals("target/workdir/groovy", compiler.getWorkDir());
        Assertions.assertTrue(
                compiler.getCompileTime() > 0, "Should take time to compile, was: " + compiler.getCompileTime());

        Assertions.assertTrue(new File("target/workdir/groovy/Cheese.class").exists());
        Assertions.assertTrue(new File("target/workdir/groovy/Dude.class").exists());

        main.stop();
    }

    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody()
                        .groovy(
                                """
                                Dude d = new Dude()
                                return d.order(header.amount)
                                """)
                        .to("mock:result");
            }
        };
    }
}
