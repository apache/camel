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
package org.apache.camel.component.activemq;

import java.util.List;

import org.apache.activemq.util.ByteSequence;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class JournalRouteTest extends ContextTestSupport {

    public void testForwardingAMessageAcrossJMSKeepingCustomJMSHeaders() throws Exception {

        byte[] payload = "Hello World".getBytes();
        
        
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        
        AssertionClause firstMessageExpectations = resultEndpoint.message(0);
        firstMessageExpectations.header("journal").isEqualTo("activemq.journal:test.a");
        firstMessageExpectations.header("location").isNotNull();
        firstMessageExpectations.body().isInstanceOf(ByteSequence.class);

        template.sendBody("direct:test.a", payload);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        ByteSequence body = (ByteSequence)exchange.getIn().getBody();
        body.compact(); // trims the byte array to the actual size.
        assertEquals("body", new String(payload), new String(body.data));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:test.a").to("activemq.journal:test.a");
                from("activemq.journal:test.a").to("mock:result");
            }
        };
    }
}