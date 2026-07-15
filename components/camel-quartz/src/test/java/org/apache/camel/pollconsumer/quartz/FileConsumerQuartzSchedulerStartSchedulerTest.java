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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;

public class FileConsumerQuartzSchedulerStartSchedulerTest extends CamelTestSupport {
    @TempDir
    Path testDirectory;

    @Test
    public void testStartSchedulerFalseMustNotPoll() throws Exception {
        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory), "Hello World", Exchange.FILE_NAME, "hello.txt");

        getMockEndpoint("mock:result").expectedMessageCount(0);

        // startScheduler=false should prevent polling even with scheduler=quartz
        await().during(3, TimeUnit.SECONDS).atMost(4, TimeUnit.SECONDS)
                .untilAsserted(() -> getMockEndpoint("mock:result").assertIsSatisfied());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(TestSupport.fileUri(testDirectory, "?scheduler=quartz&scheduler.cron=0/2+*+*+*+*+?&startScheduler=false"))
                        .to("mock:result");
            }
        };
    }
}
