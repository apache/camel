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
package org.apache.camel.component.quartz;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FromFileQuartzSchedulerTest extends BaseQuartzTest {
    protected MockEndpoint resultEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        FileUtil.removeDir(new File("target/inbox"));
        super.setUp();
    }

    @Test
    public void testQuartzRoute() throws Exception {
        resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(2);

        template.sendBody("file:target/inbox", "Hello World");
        template.sendBody("file:target/inbox", "Bye World");

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:target/inbox?scheduler=quartz&scheduler.trigger.misfireInstruction=2&scheduler.cron=0/2+*+*+*+*+?")
                        .routeId("myRoute")
                        .to("mock:result");
            }
        };
    }
}
