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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * This test shows we can poll a bean for a method and send the POJO over some transport
 *
 * @version 
 */
public class BeanMethodHeartbeatTest extends ContextTestSupport {

    protected MyService bean = new MyService("Beer");
    
    public void testHeartbeatsArrive() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMinimumMessageCount(1);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = new ArrayList<Exchange>(resultEndpoint.getReceivedExchanges());
        log.debug("Received: " + list);
        Exchange exchange = list.get(0);
        log.debug("In: " + exchange.getIn());
        log.debug("Out: " + exchange.getOut());
        Map<?, ?> map = assertIsInstanceOf(Map.class, exchange.getIn().getBody());
        log.debug("Received: " + map);
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("myService", bean);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("bean:myService?method=status").to("mock:result");
            }
        };
    }

    public static class MyService {
        private String name;

        public MyService(String name) {
            this.name = name;
        }

        public Map<String, Object> status() {
            Map<String, Object> answer = new HashMap<String, Object>();
            answer.put("name", name);
            answer.put("time", new Date());
            return answer;
        }
    }
}