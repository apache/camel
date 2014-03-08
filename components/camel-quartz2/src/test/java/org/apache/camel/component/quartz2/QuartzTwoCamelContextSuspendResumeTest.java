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
package org.apache.camel.component.quartz2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class QuartzTwoCamelContextSuspendResumeTest {

    private DefaultCamelContext camel1;
    private DefaultCamelContext camel2;

    @Before
    public void setUp() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz2://myGroup/myTimerName?cron=0/1+*+*+*+*+?").to("mock:one");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.setName("camel-2");
        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz2://myOtherGroup/myOtherTimerName?cron=0/1+*+*+*+*+?").to("mock:two");
            }
        });
        camel2.start();
    }

    @After
    public void tearDown() throws Exception {
        camel1.stop();
        camel2.stop();
    }

    @Test
    public void testTwoCamelContextRestart() throws Exception {
        MockEndpoint mock1 = camel1.getEndpoint("mock:one", MockEndpoint.class);
        mock1.expectedMinimumMessageCount(2);

        MockEndpoint mock2 = camel2.getEndpoint("mock:two", MockEndpoint.class);
        mock2.expectedMinimumMessageCount(6);
        mock1.assertIsSatisfied();

        camel1.suspend();

        mock2.assertIsSatisfied();

        // should resume triggers when we start camel 1 again
        mock1.reset();
        mock1.expectedMinimumMessageCount(2);
        camel1.resume();

        mock1.assertIsSatisfied();
    }


}
