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
package org.apache.camel.pollconsumer.quartz2;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class FileConsumerQuartzSchedulerRestartTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/file/quartz");
        super.setUp();
    }

    @Test
    public void testQuartzSchedulerRestart() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("file:target/file/quartz", "Hello World", Exchange.FILE_NAME, "hello.txt");
        context.startRoute("foo");
        assertMockEndpointsSatisfied();

        context.stopRoute("foo");
        resetMocks();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("file:target/file/quartz", "Bye World", Exchange.FILE_NAME, "bye.txt");
        context.startRoute("foo");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/file/quartz?scheduler=quartz2&scheduler.cron=0/2+*+*+*+*+?&scheduler.triggerGroup=myGroup&scheduler.triggerId=myId")
                    .routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }

}
