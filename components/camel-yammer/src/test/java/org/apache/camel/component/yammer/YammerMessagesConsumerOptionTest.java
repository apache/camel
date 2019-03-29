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
package org.apache.camel.component.yammer;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class YammerMessagesConsumerOptionTest extends YammerComponentTestSupport {

    private static final String YAMMER_MESSAGES_CONSUMER = "yammer:messages?consumerKey=aConsumerKey&consumerSecret=aConsumerSecretKey" 
        + "&accessToken=aAccessToken&limit=1&threaded=true&olderThan=58802444918784"
        + "&newerThan=58802444918781";

    @Test
    public void testOptions() throws Exception {
        YammerEndpoint endpoint = context.getEndpoint(YAMMER_MESSAGES_CONSUMER, YammerEndpoint.class);
        
        // now check if options got applied
        assertEquals(1, endpoint.getConfig().getLimit());
        assertEquals("true", endpoint.getConfig().getThreaded());
        assertEquals(58802444918784L, endpoint.getConfig().getOlderThan());
        assertEquals(58802444918781L, endpoint.getConfig().getNewerThan());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(YAMMER_MESSAGES_CONSUMER).to("mock:result");
            }
        };
    }
}
