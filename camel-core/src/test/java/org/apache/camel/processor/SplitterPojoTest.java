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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version $Revision$
 */
public class SplitterPojoTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("mySplitterBean", new MySplitterBean());
        return jndi;
    }

    public void testSplitWithPojoBean() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("James", "Jonathan", "Hadrian", "Claus", "Willem");

        template.sendBody("direct:start", "James,Jonathan,Hadrian,Claus,Willem");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:start")
                        // here we use a POJO bean mySplitterBean to do the split of the payload
                        .split().method("mySplitterBean")
                        .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    public class MySplitterBean {

        /**
         * The split method returns something that is iteratable such as a java.util.List.
         *
         * @param body the payload of the incoming message
         * @return a list containing each part splitted
         */
        public List split(String body) {
            // since this is based on an unit test you can of couse
            // use different logic for splitting as Camel have out
            // of the box support for splitting a String based on comma
            // but this is for show and tell, since this is java code
            // you have the full power how you like to split your messages
            List answer = new ArrayList();
            String[] parts = body.split(",");
            for (String part : parts) {
                answer.add(part);
            }
            return answer;
        }
    }
    // END SNIPPET: e2

}