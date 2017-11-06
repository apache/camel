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
package org.apache.camel.component.jms;

import java.io.File;
import java.io.InputStream;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsStreamMessageTypeTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/stream");
        super.setUp();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        jms.setStreamMessageTypeEnabled(true); // turn on streaming
        camelContext.addComponent("jms", jms);
        return camelContext;
    }

    @Test
    public void testStreamType() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // copy the file
        FileUtil.copyFile(new File("src/test/data/message1.xml"), new File("target/stream/in/message1.xml"));

        assertMockEndpointsSatisfied();

        StreamMessageInputStream is = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getBody(StreamMessageInputStream.class);
        assertNotNull(is);

        // no more bytes should be available on the inputstream
        assertEquals(0, is.available());

        // assert on the content of input versus output file
        String srcContent = context.getTypeConverter().mandatoryConvertTo(String.class, new File("src/test/data/message1.xml"));
        String dstContent = context.getTypeConverter().mandatoryConvertTo(String.class, new File("target/stream/out/message1.xml"));
        assertEquals("both the source and destination files should have the same content", srcContent, dstContent);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/stream/in").to("jms:queue:foo");

                from("jms:queue:foo").to("file:target/stream/out").to("mock:result");
            }
        };
    }

}
