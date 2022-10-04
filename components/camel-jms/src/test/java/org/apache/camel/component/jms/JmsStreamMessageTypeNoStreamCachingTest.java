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
package org.apache.camel.component.jms;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock("target/stream/JmsStreamMessageTypeNoStreamCachingTest")
public class JmsStreamMessageTypeNoStreamCachingTest extends AbstractJMSTest {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/stream/JmsStreamMessageTypeNoStreamCachingTest");
        super.setUp();
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, service, componentName);

        component.getConfiguration().setStreamMessageTypeEnabled(true); // turn on streaming
        return component;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setStreamCaching(false);

        return camelContext;
    }

    @ParameterizedTest
    @ValueSource(strings = { "message1.xml", "message1.txt" })
    @DisplayName("Tests stream type with both a small (message1.xml) and a large file (message1.txt)")
    public void testStreamType(String filename) throws Exception {
        getMockEndpoint("mock:resultJmsStreamMessageTypeNoStreamCachingTest").expectedMessageCount(1);

        // copy the file
        final File baseFile = new File("src/test/data/", filename);
        final File sourceFile = new File("target/stream/JmsStreamMessageTypeNoStreamCachingTest/in", filename);

        FileUtil.copyFile(baseFile, sourceFile);

        MockEndpoint.assertIsSatisfied(context);

        Object body = getMockEndpoint("mock:resultJmsStreamMessageTypeNoStreamCachingTest").getReceivedExchanges().get(0)
                .getIn().getBody();
        StreamMessageInputStream is = assertIsInstanceOf(StreamMessageInputStream.class, body);

        // no more bytes should be available on the input stream
        assertEquals(0, is.available());

        // assert on the content of input versus output file
        String srcContent = context.getTypeConverter().mandatoryConvertTo(String.class, baseFile);
        String dstContent
                = context.getTypeConverter().mandatoryConvertTo(String.class,
                        new File("target/stream/JmsStreamMessageTypeNoStreamCachingTest/out/", filename));
        assertEquals(srcContent, dstContent, "both the source and destination files should have the same content");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:target/stream/JmsStreamMessageTypeNoStreamCachingTest/in")
                        .to("jms:queue:JmsStreamMessageTypeNoStreamCachingTest");

                from("jms:queue:JmsStreamMessageTypeNoStreamCachingTest")
                        .to("file:target/stream/JmsStreamMessageTypeNoStreamCachingTest/out")
                        .to("mock:resultJmsStreamMessageTypeNoStreamCachingTest");
            }
        };
    }

}
