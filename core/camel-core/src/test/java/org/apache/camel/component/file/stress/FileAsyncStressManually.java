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
package org.apache.camel.component.file.stress;

import java.util.Random;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;

@Ignore("Manual test")
public class FileAsyncStressManually extends ContextTestSupport {

    public void testAsyncStress() throws Exception {
        // do not test on windows
        if (isPlatform("windows")) {
            return;
        }

        // test by starting the unit test FileAsyncStressFileDropper in another
        // JVM

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(250);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/filestress?readLock=markerFile&maxMessagesPerPoll=25&move=backup").threads(10).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // simulate some work with random time to complete
                        Random ran = new Random();
                        int delay = ran.nextInt(500) + 10;
                        Thread.sleep(delay);
                    }
                }).to("mock:result");
            }
        };
    }

}
