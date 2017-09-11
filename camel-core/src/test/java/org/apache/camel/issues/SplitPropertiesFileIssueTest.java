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
package org.apache.camel.issues;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class SplitPropertiesFileIssueTest extends ContextTestSupport {

    private String body = "foo=1" + LS + "bar=2" + LS + "bar=3" + LS + "foo=4";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/file/splitprop");
        super.setUp();
    }

    public void testSplitPropertiesFileAndRoute() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("[foo=1, foo=4]");

        // after the file is routed it should be moved to done
        foo.expectedFileExists("target/file/splitprop/done/myprop.txt", body);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedBodiesReceived("[bar=2, bar=3]");

        template.sendBodyAndHeader("file://target/file/splitprop", body, Exchange.FILE_NAME, "myprop.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file/splitprop?initialDelay=0&delay=10&move=done")
                    .convertBodyTo(String.class)
                    .split(new MyCustomExpression())
                    .recipientList(header("myCustomDestination"));
            }
        };
    }

    private static class MyCustomExpression implements Expression {

        @SuppressWarnings("unchecked")
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            // must copy from the original exchange as Camel holds information about the file in progress
            Message msg1 = exchange.getIn().copy();
            Message msg2 = exchange.getIn().copy();

            // now we use our own expressions to split the file as we like it
            // what we return is just the list of the two Camel Message objects
            // which contains the splitted data (our way)
            List<Message> answer = new ArrayList<Message>();
            answer.add(msg1);
            answer.add(msg2);

            // split the original body into two data lists
            // can be done a bit prettier than this code
            // but its just for show and tell how to use Expressions
            List<String> data1 = new ArrayList<String>();
            List<String> data2 = new ArrayList<String>();

            String body = exchange.getIn().getBody(String.class);
            String[] lines = body.split(LS);
            for (String line : lines) {
                if (line.startsWith("foo")) {
                    data1.add(line);
                } else {
                    data2.add(line);
                }
            }

            // as we use the recipientList afterwards we set the destination
            // as well on our message where we want to route it

            // as we are an unit test then just store the list using toString so its easier to test
            msg1.setBody(data1.toString());
            msg1.setHeader("myCustomDestination", "mock:foo");
            msg2.setBody(data2.toString());
            msg2.setHeader("myCustomDestination", "mock:bar");

            // just cast it to T as its safe as its Object anyway for custom expressions
            return (T) answer;
        }
    }
}
