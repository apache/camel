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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileConsumerQuartzSchedulerStartSchedulerTest extends CamelTestSupport {
    @TempDir
    Path testDirectory;

    @Test
    public void testStartSchedulerFalseMustNotPoll() throws Exception {
        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory), "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        // the cron fires every 2 seconds, so wait longer than that and verify still no message was received
        mock.setAssertPeriod(3000);

        // startScheduler=false should prevent polling even with scheduler=quartz
        mock.assertIsSatisfied();
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
