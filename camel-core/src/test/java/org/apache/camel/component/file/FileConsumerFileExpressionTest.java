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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * Unit test for expression option for file consumer.
 */
public class FileConsumerFileExpressionTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/filelanguage");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("counter", new MyGuidGenerator());
        return jndi;
    }

    public void testConsumeFileBasedOnBeanName() throws Exception {
        template.sendBodyAndHeader("file://target/filelanguage/bean", "Hello World", Exchange.FILE_NAME, "122.txt");
        template.sendBodyAndHeader("file://target/filelanguage/bean", "Goodday World", Exchange.FILE_NAME, "123.txt");
        template.sendBodyAndHeader("file://target/filelanguage/bean", "Bye World", Exchange.FILE_NAME, "124.txt");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/filelanguage/bean/"
                      + "?initialDelay=0&delay=10&fileName=${bean:counter.next}.txt&delete=true").to("mock:result");
            }
        });

        // we should only get one as we only poll a single file using the file expression
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Goodday World");

        context.start();

        assertMockEndpointsSatisfied();
    }

    public void testConsumeFileBasedOnDatePattern() throws Exception {
        template.sendBodyAndHeader("file://target/filelanguage/date", "Bye World", Exchange.FILE_NAME, "myfile-20081128.txt");
        template.sendBodyAndHeader("file://target/filelanguage/date", "Hello World", Exchange.FILE_NAME, "myfile-20081129.txt");
        template.sendBodyAndHeader("file://target/filelanguage/date", "Goodday World", Exchange.FILE_NAME, simple("myfile-${date:now:yyyyMMdd}.txt"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("file://target/filelanguage/date/"
                      + "?initialDelay=0&delay=10&fileName=myfile-${date:now:yyyyMMdd}.txt").convertBodyTo(String.class).to("mock:result");
                // END SNIPPET: e1
            }
        });

        // we should only get one as we only poll a single file using the file expression
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Goodday World");

        context.start();

        assertMockEndpointsSatisfied();
    }

    public class MyGuidGenerator {
        public String next() {
            return "123";
        }
    }

}