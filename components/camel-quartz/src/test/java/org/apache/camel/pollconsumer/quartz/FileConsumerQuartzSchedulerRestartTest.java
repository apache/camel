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
package org.apache.camel.pollconsumer.quartz;

import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileConsumerQuartzSchedulerRestartTest extends CamelTestSupport {
    @TempDir
    Path testDirectory;

    @Test
    public void testQuartzSchedulerRestart() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader(fileUri(testDirectory), "Hello World", Exchange.FILE_NAME, "hello.txt");
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().stopRoute("foo");
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader(fileUri(testDirectory), "Bye World", Exchange.FILE_NAME, "bye.txt");
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(testDirectory,
                        "?scheduler=quartz&scheduler.cron=0/2+*+*+*+*+?&scheduler.triggerGroup=myGroup&scheduler.triggerId=myId"))
                                .routeId("foo").noAutoStartup()
                                .to("mock:result");
            }
        };
    }

}
