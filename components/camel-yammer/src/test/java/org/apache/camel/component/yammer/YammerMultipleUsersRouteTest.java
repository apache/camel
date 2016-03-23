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
package org.apache.camel.component.yammer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.yammer.model.User;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Online access to yammer and fails with 401 authentication error")
public class YammerMultipleUsersRouteTest extends YammerComponentTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testConsumeAllUsers() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        
        template.sendBody("direct:start", "overwrite me");        
        
        assertMockEndpointsSatisfied();
        
        Exchange exchange = mock.getExchanges().get(0);
        List<User> users = exchange.getIn().getBody(List.class);

        assertEquals(2, users.size());
        assertEquals("Joe Camel", users.get(0).getFullName());        
        assertEquals("jcamel@redhat.com", users.get(0).getContact().getEmailAddresses().get(0).getAddress());
        assertEquals("Joe Camel Jr", users.get(1).getFullName());        
        assertEquals("jcameljr@redhat.com", users.get(1).getContact().getEmailAddresses().get(0).getAddress());
    }

    @Override
    protected String jsonFile() {
        return "/users.json";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").pollEnrich("yammer:users?consumerKey=aConsumerKey&consumerSecret=aConsumerSecretKey&accessToken=aAccessToken").to("mock:result");
            }
        };
    }
}
