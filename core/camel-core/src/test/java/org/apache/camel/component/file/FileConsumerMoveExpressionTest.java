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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for expression option for file consumer.
 */
public class FileConsumerMoveExpressionTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filelanguage");
        super.setUp();
    }

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
                from("file://target/data/filelanguage/?initialDelay=0&delay=10&exclude=.*bak" + "&move=${id}.bak").convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/data/filelanguage/", "Hello World", Exchange.FILE_NAME, "report.txt");
        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        String id = mock.getExchanges().get(0).getIn().getMessageId();
        File file = new File("target/data/filelanguage/" + id + ".bak");
        assertTrue("File should have been renamed", file.exists());
    }

    @Test
    public void testRenameToComplexWithId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filelanguage/?initialDelay=0&delay=10&exclude=.*bak" + "&move=backup-${id}-${file:name.noext}.bak").convertBodyTo(String.class)
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("file://target/data/filelanguage/", "Bye World", Exchange.FILE_NAME, "report2.txt");
        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        String id = mock.getExchanges().get(0).getIn().getMessageId();
        File file = new File("target/data/filelanguage/backup-" + id + "-report2.bak");
        assertTrue("File should have been renamed", file.exists());
    }

    @Test
    public void testRenameToBean() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filelanguage/?initialDelay=0&delay=10&exclude=.*bak" + "&move=backup/${bean:myguidgenerator.guid}.txt").convertBodyTo(String.class)
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Big World");
        mock.expectedFileExists("target/data/filelanguage/backup/123.txt", "Bye Big World");

        template.sendBodyAndHeader("file://target/data/filelanguage/", "Bye Big World", Exchange.FILE_NAME, "report3.txt");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRenameToSiblingFolder() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filelanguage/?initialDelay=0&delay=10&exclude=.*bak" + "&move=../backup/${file:name}.bak").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Big World");
        mock.expectedFileExists("target/data/backup/report4.txt.bak");

        template.sendBodyAndHeader("file://target/data/filelanguage/", "Hello Big World", Exchange.FILE_NAME, "report4.txt");
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
                endpoint.setFile(new File("target/data/filelanguage/"));
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
        mock.expectedFileExists("target/data/filelanguage/123");

        template.sendBodyAndHeader("file://target/data/filelanguage/", "Bean Language Rules The World", Exchange.FILE_NAME, "report5.txt");
        assertMockEndpointsSatisfied();
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }

}
