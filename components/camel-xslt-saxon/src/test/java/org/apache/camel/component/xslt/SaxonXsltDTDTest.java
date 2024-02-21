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
package org.apache.camel.component.xslt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SaxonXsltDTDTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SaxonXsltDTDTest.class);

    private static final String MESSAGE
            = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc//user//test\">]><task><name>&xxe;</name></task>";

    @Test
    public void testSendingStringMessage() throws Exception {
        sendEntityMessage(MESSAGE);
    }

    @Test
    public void testSendingInputStreamMessage() throws Exception {
        InputStream is = new ByteArrayInputStream(MESSAGE.getBytes());
        sendEntityMessage(is);
    }

    private void sendEntityMessage(Object message) throws Exception {

        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.reset();
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:start1", message);

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);
        assertTrue(xml.indexOf("<transformed subject=\"\">") > 0, "Get a wrong transformed message");

        endpoint.reset();
        endpoint.expectedMessageCount(1);

        // reset stream before trying again
        if (message instanceof InputStream) {
            ((InputStream) message).reset();
        }
        try {
            template.sendBody("direct:start2", message);
            list = endpoint.getReceivedExchanges();
            exchange = list.get(0);
            xml = exchange.getIn().getBody(String.class);
            assertTrue(xml.indexOf("<transformed subject=\"\">") > 0, "Get a wrong transformed message");
        } catch (Exception ex) {
            LOG.warn("Exception thrown during test (likely safe to ignore): {}", ex.getMessage(), ex);
            // expect an exception here
            assertTrue(ex instanceof CamelExecutionException, "Get a wrong exception");
            // the file could not be found
            assertTrue(ex.getCause() instanceof TransformerException, "Get a wrong exception cause");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:start1")
                        .to("xslt-saxon:org/apache/camel/component/xslt/transform_dtd.xsl")
                        .to("mock:result");

                from("direct:start2")
                        .to("xslt-saxon:org/apache/camel/component/xslt/transform_dtd.xsl?allowStAX=false")
                        .to("mock:result");
            }
        };
    }

}
