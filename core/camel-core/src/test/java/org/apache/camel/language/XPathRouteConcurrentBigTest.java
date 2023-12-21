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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XPathRouteConcurrentBigTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(XPathRouteConcurrentBigTest.class);

    private static final String XMLTEST1 = "<message><messageType>AAA</messageType><sender>0123456789101112131415</sender>"
                                           + "<rawData>Uyw7TSVkUMxUyw7TSgGUMQAyw7TSVkUMxUyA7TSgGUMQAyw7TSVkUMxUyA</rawData>"
                                           + "<sentDate>2009-10-12T12:22:02+02:00</sentDate> <receivedDate>2009-10-12T12:23:31.248+02:00</receivedDate>"
                                           + "<intproperty>1</intproperty><stringproperty>aaaaaaabbbbbbbccccccccdddddddd</stringproperty></message>";
    private static final String XMLTEST2 = "<message><messageType>AAB</messageType><sender>0123456789101112131415</sender>"
                                           + "<rawData>Uyw7TSVkUMxUyw7TSgGUMQAyw7TSVkUMxUyA7TSgGUMQAyw7TSVkUMxUyA</rawData>"
                                           + "<sentDate>2009-10-12T12:22:02+02:00</sentDate> <receivedDate>2009-10-12T12:23:31.248+02:00</receivedDate>"
                                           + "<intproperty>1</intproperty><stringproperty>aaaaaaabbbbbbbccccccccdddddddd</stringproperty></message>";
    private static final String XMLTEST3 = "<message><messageType>ZZZ</messageType><sender>0123456789101112131415</sender>"
                                           + "<rawData>Uyw7TSVkUMxUyw7TSgGUMQAyw7TSVkUMxUyA7TSgGUMQAyw7TSVkUMxUyA</rawData>"
                                           + "<sentDate>2009-10-12T12:22:02+02:00</sentDate> <receivedDate>2009-10-12T12:23:31.248+02:00</receivedDate>"
                                           + "<intproperty>1</intproperty><stringproperty>aaaaaaabbbbbbbccccccccdddddddd</stringproperty></message>";

    @Test
    public void testConcurrent() throws Exception {
        doSendMessages(333);
    }

    private void doSendMessages(int messageCount) throws Exception {
        LOG.info("Sending {} messages", messageCount);

        int forResult = (messageCount * 2 / 3) + messageCount % 3;
        int forOther = messageCount - forResult;

        StopWatch watch = new StopWatch();

        // give more time on slow servers
        getMockEndpoint("mock:result").setResultWaitTime(30000);
        getMockEndpoint("mock:other").setResultWaitTime(30000);

        getMockEndpoint("mock:result").expectedMessageCount(forResult);
        getMockEndpoint("mock:other").expectedMessageCount(forOther);

        for (int i = 0; i < messageCount; i++) {
            switch (i % 3) {
                case 0:
                    template.sendBody("seda:foo", XMLTEST1);
                    break;
                case 1:
                    template.sendBody("seda:foo", XMLTEST2);
                    break;
                case 2:
                    template.sendBody("seda:foo", XMLTEST3);
                    break;
                default:
                    break;
            }
        }

        LOG.info("Sent {} messages in {} ms", messageCount, watch.taken());

        assertMockEndpointsSatisfied();

        LOG.info("Processed {} messages in {} ms", messageCount, watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?concurrentConsumers=50&size=250000").choice().when()
                        .xpath("//messageType = 'AAA' or " + "//messageType = 'AAB' or " + "//messageType = 'AAC' or "
                               + "//messageType = 'AAD' or " + "//messageType = 'AAE' or "
                               + "//messageType = 'AAF' or " + "//messageType = 'AAG' or " + "//messageType = 'AAH' or "
                               + "//messageType = 'AAI' or " + "//messageType = 'AAJ' or "
                               + "//messageType = 'AAK' or " + "//messageType = 'AAL' or " + "//messageType = 'AAM' or "
                               + "//messageType = 'AAN' or " + "//messageType = 'AAO' or "
                               + "//messageType = 'AAP' or " + "//messageType = 'AAQ' or " + "//messageType = 'AAR' or "
                               + "//messageType = 'AAS' or " + "//messageType = 'AAT' or "
                               + "//messageType = 'AAU' or " + "//messageType = 'AAV' or " + "//messageType = 'AAW' or "
                               + "//messageType = 'AAX' or " + "//messageType = 'AAY'")
                        .to("mock:result").otherwise().to("mock:other").end();
            }
        };
    }

}
