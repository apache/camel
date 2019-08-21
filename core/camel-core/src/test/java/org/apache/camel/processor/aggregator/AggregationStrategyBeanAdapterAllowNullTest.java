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
package org.apache.camel.processor.aggregator;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AggregationStrategyBeanAdapterAllowNullTest extends ContextTestSupport {

    private MyUserAppender appender = new MyUserAppender();

    @Test
    public void testAggregate() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", new User("Claus"));
        template.sendBody("direct:start", new User("James"));
        template.sendBody("direct:start", new User("Jonathan"));

        assertMockEndpointsSatisfied();

        List<?> names = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Jonathan", names.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(constant(true), AggregationStrategies.beanAllowNull(appender, "addUsers")).completionSize(3).to("mock:result");
            }
        };
    }

    public static final class MyUserAppender {

        public List<String> addUsers(List<String> names, User user) {
            if (names == null) {
                names = new ArrayList<>();
            }
            names.add(user.getName());
            return names;
        }
    }

    /**
     * We support annotations on the types.
     */
    @XmlRootElement(name = "user")
    public static final class User {
        private String name;

        public User(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
