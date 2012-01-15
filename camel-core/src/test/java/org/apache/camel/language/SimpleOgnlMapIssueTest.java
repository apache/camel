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
package org.apache.camel.language;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Based on user forum issue
 */
public class SimpleOgnlMapIssueTest extends ContextTestSupport {

    public void testSimpleOgnlIssueKing() throws Exception {
        getMockEndpoint("mock:king").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        MyObjectMessage body = new MyObjectMessage();
        body.getProperty().put("foo", "King Kong");
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    public void testSimpleOgnlIssueOther() throws Exception {
        getMockEndpoint("mock:king").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        MyObjectMessage body = new MyObjectMessage();
        body.getProperty().put("foo", "Tiger");
        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when().simple("${body.property['foo']} == 'King Kong'")
                            .to("mock:king")
                        .otherwise()
                            .to("mock:other");
            }
        };
    }

    public static final class MyObjectMessage {
        private Map<Object, Object> property;

        public MyObjectMessage() {
            this.property = new HashMap<Object, Object>();
        }

        public Map<Object, Object> getProperty() {
            return property;
        }
    }
}
