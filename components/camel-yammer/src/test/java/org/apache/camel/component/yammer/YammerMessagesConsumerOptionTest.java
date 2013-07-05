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

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class YammerMessagesConsumerOptionTest extends YammerComponentTestSupport {

    @Test
    public void testOptions() throws Exception {
        // now check if options got applied
        assertEquals(1, yammerComponent.getConfig().getLimit());
        assertEquals("true", yammerComponent.getConfig().getThreaded());
        assertEquals(130, yammerComponent.getConfig().getOlderThan());
        assertEquals(127, yammerComponent.getConfig().getNewerThan());
        //assertEquals(YammerConstants.YAMMER_BASE_API_URL + "messages.json?limit=1&older_than=130&newer_than=127&threaded=true", yammerComponent.getConfig().getApiUrl());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("yammer:messages?consumerKey=aConsumerKey&consumerSecret=aConsumerSecretKey&accessToken=aAccessToken&limit=1&threaded=true&olderThan=130&newerThan=127").to("mock:result");
            }
        };
    }
}
