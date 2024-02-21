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
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test cannot run in parallel: it reuses the same path for different test iterations
 */
@ResourceLock("src/test/data")
public class JmsStreamMessageTypeTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new TransientCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @AfterEach
    public void setUp() throws Exception {
        deleteDirectory("target/stream/JmsStreamMessageTypeTest");
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, service, componentName);

        component.getConfiguration().setStreamMessageTypeEnabled(true); // turn on streaming
        return component;
    }

    @ParameterizedTest
    @ValueSource(strings = { "message1.xml", "message1.txt" })
    @DisplayName("Tests stream type with both a small (message1.xml) and a large file (message1.txt)")
    public void testStreamType(String filename) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        final File baseFile = new File("src/test/data", filename);
        final File sourceFile = new File("target/stream/JmsStreamMessageTypeTest/in/", filename);

        // copy the file
        FileUtil.copyFile(baseFile, sourceFile);

        MockEndpoint.assertIsSatisfied(context);

        Object body = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getBody();
        InputStream is = assertIsInstanceOf(InputStream.class, body);

        // assert on the content of input versus output file
        String srcContent = context.getTypeConverter().mandatoryConvertTo(String.class, new File("src/test/data/", filename));
        String dstContent
                = context.getTypeConverter().mandatoryConvertTo(String.class,
                        new File("target/stream/JmsStreamMessageTypeTest/out/", filename));
        assertEquals(srcContent, dstContent, "both the source and destination files should have the same content");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:target/stream/JmsStreamMessageTypeTest/in").to("jms:queue:JmsStreamMessageTypeTest");

                from("jms:queue:JmsStreamMessageTypeTest").to("file:target/stream/JmsStreamMessageTypeTest/out")
                        .to("mock:result");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
