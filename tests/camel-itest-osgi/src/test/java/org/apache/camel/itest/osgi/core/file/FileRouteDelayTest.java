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
package org.apache.camel.itest.osgi.core.file;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class FileRouteDelayTest extends OSGiIntegrationTestSupport {

    @Test
    public void testFileRouteDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");
        // should be moved to .camel when done
        mock.expectedFileExists("target/data/.camel/hello.txt");
        mock.expectedFileExists("target/data/.camel/bye.txt");

        StopWatch watch = new StopWatch();

        template.sendBodyAndHeader("file:target/data", "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(3000);

        template.sendBodyAndHeader("file:target/data", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        long delta = watch.stop();
        assertTrue("Should take 6 sec or longer, was " + delta, delta > 5500);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // delete the data directory
                deleteDirectory("target/data");

                from("file:target/data?delay=6000").convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
