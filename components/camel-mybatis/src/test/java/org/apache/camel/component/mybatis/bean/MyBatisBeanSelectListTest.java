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
package org.apache.camel.component.mybatis.bean;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.mybatis.Account;
import org.apache.camel.component.mybatis.MyBatisTestSupport;
import org.junit.Test;

public class MyBatisBeanSelectListTest extends MyBatisTestSupport {

    @Test
    public void testSelectList() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();

        List<?> list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        Account james = (Account) list.get(0);
        Account claus = (Account) list.get(1);
        assertEquals("James", james.getFirstName());
        assertEquals("Claus", claus.getFirstName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mybatis-bean:AccountService:selectBeanAllAccounts")
                    .to("mock:result");
            }
        };
    }

}
