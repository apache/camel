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
package org.apache.camel.component.aws.s3;

import java.io.InputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class S3ComponentSpringTest extends CamelSpringTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody("This is my bucket content.");
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0));

        assertResponseMessage(exchange.getIn());
    }

    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody("This is my bucket content.");
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0));

        assertResponseMessage(exchange.getMessage());
    }

    private void assertResultExchange(Exchange resultExchange) {
        assertIsInstanceOf(InputStream.class, resultExchange.getIn().getBody());
        assertEquals("This is my bucket content.", resultExchange.getIn().getBody(String.class));
        assertEquals("mycamelbucket", resultExchange.getIn().getHeader(S3Constants.BUCKET_NAME));
        assertEquals("CamelUnitTest", resultExchange.getIn().getHeader(S3Constants.KEY));
        assertNull(resultExchange.getIn().getHeader(S3Constants.VERSION_ID)); // not
                                                                              // enabled
                                                                              // on
                                                                              // this
                                                                              // bucket
        assertNull(resultExchange.getIn().getHeader(S3Constants.LAST_MODIFIED));
        assertNull(resultExchange.getIn().getHeader(S3Constants.E_TAG));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_TYPE));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_ENCODING));
        assertEquals(0L, resultExchange.getIn().getHeader(S3Constants.CONTENT_LENGTH));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_DISPOSITION));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_MD5));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CACHE_CONTROL));
    }

    private void assertResponseMessage(Message message) {
        assertEquals("3a5c8b1ad448bca04584ecb55b836264", message.getHeader(S3Constants.E_TAG));
        assertNull(message.getHeader(S3Constants.VERSION_ID));
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws/s3/S3ComponentSpringTest-context.xml");
    }
}
