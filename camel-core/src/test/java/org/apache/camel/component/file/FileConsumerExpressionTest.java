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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.language.bean.BeanLanguage;

/**
 * Unit test for expression option for file consumer.
 */
public class FileConsumerExpressionTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filelanguage");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myguidgenerator", new MyGuidGenerator());
        return jndi;
    }

    public void testRenameToId() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/filelanguage/", "Hello World", FileComponent.HEADER_FILE_NAME, "report.txt");
        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        String id = mock.getExchanges().get(0).getIn().getMessageId();
        File file = new File("target/filelanguage/" + id + ".bak");
        file = file.getAbsoluteFile();
        assertTrue("File should have been renamed", file.exists());
    }

    public void testRenameToComplexWithId() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("file://target/filelanguage/", "Bye World", FileComponent.HEADER_FILE_NAME, "report2.txt");
        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        String id = mock.getExchanges().get(0).getIn().getMessageId();
        File file = new File("target/filelanguage/backup-" + id + "-report2.bak");
        file = file.getAbsoluteFile();
        assertTrue("File should have been renamed", file.exists());
    }

    public void testRenameToBean() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Big World");

        template.sendBodyAndHeader("file://target/filelanguage/", "Bye Big World", FileComponent.HEADER_FILE_NAME, "report3.txt");
        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        File file = new File("target/filelanguage/backup/123.txt");
        file = file.getAbsoluteFile();
        assertTrue("File should have been renamed", file.exists());
    }

    public void testRenameToSiblingFolder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Big World");

        template.sendBodyAndHeader("file://target/filelanguage/", "Hello Big World", FileComponent.HEADER_FILE_NAME, "report4.txt");
        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        File file = new File("target/backup/report4.txt.bak");
        file = file.getAbsoluteFile();
        assertTrue("File should have been renamed", file.exists());
    }

    public void testRenameToBeanWithBeanLanguage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bean Language Rules The World");

        template.sendBodyAndHeader("file://target/filelanguage/", "Bean Language Rules The World",
                FileComponent.HEADER_FILE_NAME, "report5.txt");
        assertMockEndpointsSatisfied();

        // give time for consumer to rename file
        Thread.sleep(1000);

        File file = new File("target/filelanguage/123");
        file = file.getAbsoluteFile();
        assertTrue("File should have been renamed", file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/filelanguage/report.txt?autoCreate=false"
                     + "&expression=${id}.bak").to("mock:result");

                from("file://target/filelanguage/report2.txt?autoCreate=false"
                     + "&expression=backup-${id}-${file:name.noext}.bak").to("mock:result");

                from("file://target/filelanguage/report3.txt?autoCreate=false"
                     + "&expression=backup/${bean:myguidgenerator.guid}.txt").to("mock:result");

                from("file://target/filelanguage/report4.txt?autoCreate=false"
                     + "&expression=../backup/${file:name}.bak").to("mock:result");

                // configured by java using java beans setters
                FileEndpoint endpoint = new FileEndpoint();
                endpoint.setCamelContext(context);
                endpoint.setFile(new File("target/filelanguage/report5.txt"));
                endpoint.setAutoCreate(false);
                endpoint.setExpression(BeanLanguage.bean("myguidgenerator"));
                from(endpoint).to("mock:result");
            }
        };
    }

    public class MyGuidGenerator {
        public String guid() {
            return "123";
        }
    }

}
