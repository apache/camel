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
package org.apache.camel.example;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Body;
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbErrorLogTest extends CamelTestSupport {

    @Test
    public void testErrorHandling() throws Exception {
        // the 2nd message is set to fail, but the 4 others should be routed
        getMockEndpoint("mock:end").expectedMessageCount(4);

        // FailingBean will cause message at index 2 to throw exception
        for (int i = 0; i < 5; i++) {
            sendBody("seda:test", new CannotMarshal(i));
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:test")
                    .bean(new FailingBean())
                    .to("log:end", "mock:end");
            }
        };
    }

    public static final class FailingBean {
        @Handler
        public void handle(@Body CannotMarshal body) {
            if (body.getMessageNo() == 2) {
                // fail on second message
                throw new RuntimeException("Kaboom");
            }
        }
    }

    /**
     * This class will throw RuntimeException on JAXB marshal
     */
    @XmlRootElement
    public static final class CannotMarshal {

        private int messageNo;

        public CannotMarshal() {
        }

        public CannotMarshal(int messageNo) {
            this.messageNo = messageNo;
        }

        public int getMessageNo() {
            return messageNo;
        }

        public void setMessageNo(int messageNo) {
            this.messageNo = messageNo;
        }

        public void setUhoh(String name) {
        }

        public String getUhoh() {
            throw new RuntimeException("Can't marshal this");
        }

        @Override
        public String toString() {
            return "MessageNo. " + messageNo;
        }
    }

}