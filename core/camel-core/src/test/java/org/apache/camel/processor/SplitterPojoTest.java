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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SplitterPojoTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("mySplitterBean", new MySplitterBean());
        return jndi;
    }

    @Test
    public void testSplitBodyWithPojoBean() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedBodiesReceived("James", "Jonathan", "Hadrian", "Claus", "Willem");

        template.sendBody("direct:body", "James,Jonathan,Hadrian,Claus,Willem");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitMessageWithPojoBean() throws Exception {
        String users[] = { "James", "Jonathan", "Hadrian", "Claus", "Willem" };
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(5);
        template.sendBodyAndHeader("direct:message", "Test Body Message", "user", "James,Jonathan,Hadrian,Claus,Willem");
        int i = 0;
        for (Exchange exchange : mock.getExchanges()) {
            assertEquals("Test Body Message", exchange.getIn().getBody(), "We got a wrong body ");
            assertEquals(users[i], exchange.getIn().getHeader("user"), "We got a wrong header ");
            i++;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:body")
                        // here we use a POJO bean mySplitterBean to do the split of
                        // the payload
                        .split().method("mySplitterBean", "splitBody").to("mock:result");
                from("direct:message")
                        // here we use a POJO bean mySplitterBean to do the split of
                        // the message
                        // with a certain header value
                        .split().method("mySplitterBean", "splitMessage").to("mock:result");
                // END SNIPPET: e1

            }
        };
    }

    // START SNIPPET: e2
    public static class MySplitterBean {

        /**
         * The split body method returns something that is iteratable such as a java.util.List.
         *
         * @param  body the payload of the incoming message
         * @return      a list containing each part split
         */
        public List<String> splitBody(String body) {
            // since this is based on an unit test you can of cause
            // use different logic for splitting as Camel have out
            // of the box support for splitting a String based on comma
            // but this is for show and tell, since this is java code
            // you have the full power how you like to split your messages
            String[] parts = body.split(",");
            List<String> answer = new ArrayList<>(Arrays.asList(parts));
            return answer;
        }

        /**
         * The split message method returns something that is iteratable such as a java.util.List.
         *
         * @param  header the header of the incoming message with the name user
         * @param  body   the payload of the incoming message
         * @return        a list containing each part split
         */
        public List<Message> splitMessage(@Header(value = "user") String header, @Body String body, CamelContext camelContext) {
            // we can leverage the Parameter Binding Annotations
            // http://camel.apache.org/parameter-binding-annotations.html
            // to access the message header and body at same time,
            // then create the message that we want, splitter will
            // take care rest of them.
            // *NOTE* this feature requires Camel version >= 1.6.1
            List<Message> answer = new ArrayList<>();
            String[] parts = header.split(",");
            for (String part : parts) {
                DefaultMessage message = new DefaultMessage(camelContext);
                message.setHeader("user", part);
                message.setBody(body);
                answer.add(message);
            }
            return answer;
        }
    }
    // END SNIPPET: e2

}
