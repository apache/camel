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
package org.apache.camel.facebook;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import facebook4j.api.SearchMethods;

public class FacebookComponentConsumerTest extends CamelFacebookTestSupport {

    private final Set<String> searchNames = new HashSet<String>();

    public FacebookComponentConsumerTest() throws Exception {
        // find search methods for consumer tests
        for (Method method : SearchMethods.class.getDeclaredMethods()) {
            String name = method.getName();
            if (name.startsWith("search") && !"search".equals(name)) {
                name = Character.toLowerCase(name.charAt(6)) + name.substring(7);
            }
            if (!"locations".equals(name) && !"checkins".equals(name)) {
                searchNames.add(name);
            }
        }
    }

    @Test
    public void testConsumers() throws InterruptedException {
        for (String name : searchNames) {
            MockEndpoint mock = getMockEndpoint("mock:consumeResult" + name);
            mock.expectedMinimumMessageCount(1);
        }
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                // start with a 7 day window for the first delayed poll
                String since = new SimpleDateFormat(FacebookConstants.FACEBOOK_DATE_FORMAT).format(
                    new Date(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)));

                for (String name : searchNames) {
                    from("facebook://" + name + "?query=cheese&reading.limit=10&reading.locale=en.US&reading.since="
                        + since + "&consumer.initialDelay=1000&" + getOauthParams())
                        .to("mock:consumeResult" + name);
                }

                // TODO add tests for the rest of the supported methods
            }
        };
    }

}
