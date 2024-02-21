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
package org.apache.camel.component.file;

import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for expression option for file consumer.
 */
public class FileConsumerMoveExpressionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }

    @Test
    public void testRenameToId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&exclude=.*bak" + "&move=${id}.bak"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "report.txt");
        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        String id = mock.getExchanges().get(0).getIn().getMessageId();
        assertTrue(Files.exists(testFile(id + ".bak")), "File should have been renamed");
    }

    @Test
    public void testRenameToComplexWithId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&exclude=.*bak&move=backup-${id}-${file:name.noext}.bak"))
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "report2.txt");
        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        String id = mock.getExchanges().get(0).getIn().getMessageId();
        assertTrue(Files.exists(testFile("backup-" + id + "-report2.bak")), "File should have been renamed");
    }

    @Test
    public void testRenameToBean() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?initialDelay=0&delay=10&exclude=.*bak&move=backup/${bean:myguidgenerator.guid}.txt"))
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Big World");
        mock.expectedFileExists(testFile("backup/123.txt"), "Bye Big World");

        template.sendBodyAndHeader(fileUri(), "Bye Big World", Exchange.FILE_NAME, "report3.txt");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRenameToSiblingFolder() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("test?initialDelay=0&delay=10&exclude=.*bak&move=../backup/${file:name}.bak"))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Big World");
        mock.expectedFileExists(testFile("backup/report4.txt.bak"));

        template.sendBodyAndHeader(fileUri("test"), "Hello Big World", Exchange.FILE_NAME, "report4.txt");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRenameToBeanWithBeanLanguage() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configured by java using java beans setters
                FileEndpoint endpoint = new FileEndpoint();
                endpoint.setCamelContext(context);
                endpoint.setFile(testDirectory().toFile());
                endpoint.setAutoCreate(false);
                endpoint.setMove(new BeanExpression("myguidgenerator", null));
                endpoint.setExclude(".*bak");
                endpoint.setInitialDelay(10);

                from(endpoint).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bean Language Rules The World");
        mock.expectedFileExists(testFile("123"));

        template.sendBodyAndHeader(fileUri(), "Bean Language Rules The World", Exchange.FILE_NAME,
                "report5.txt");
        assertMockEndpointsSatisfied();
    }

    public static class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }

}
