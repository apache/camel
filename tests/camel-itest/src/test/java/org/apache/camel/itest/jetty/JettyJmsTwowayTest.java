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
package org.apache.camel.itest.jetty;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

@ContextConfiguration
public class JettyJmsTwowayTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    public void testSendingRequest() throws Exception {
        assertNotNull("the camelContext should not be null", camelContext);
        ProducerTemplate<Exchange> template = camelContext.createProducerTemplate();
        Exchange exchange = template.send("jetty:http://localhost:9000/test", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<hello>Willem</hello>");
                exchange.getIn().setHeader("Operation", "greetMe");
            }

        });
        assertEquals("get result ", "<message>out</message>", exchange.getOut().getBody(String.class));
        Thread.sleep(2000);

    }
}
