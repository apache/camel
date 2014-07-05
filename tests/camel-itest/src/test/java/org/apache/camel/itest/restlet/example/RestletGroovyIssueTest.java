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
package org.apache.camel.itest.restlet.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class RestletGroovyIssueTest extends CamelTestSupport {

    private long port = AvailablePortFinder.getNextAvailable(16000);
    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Test
    public void testRestletGroovy() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(10);
        getMockEndpoint("mock:output").expectedBodiesReceivedInAnyOrder("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        
        for (int i = 0; i < 10; i++) {
            final Integer num = i;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    String s = "" + num;
                    Object response = template.requestBody("restlet:http://localhost:" + port + "/foo/" + s + "?restletMethod=GET", "");
                    assertEquals(s, response);
                };
            });
        }

        assertMockEndpointsSatisfied();

        executorService.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("restlet:http://localhost:%s/foo/{id}", port)
                    .to("log:input?showHeaders=true")
                    .to("mock:input")
                    .transform().groovy("request.headers.id")
                    // sleep a bit so multiple threads are in use
                    .delay(1000)
                    .to("log:output")
                    .to("mock:output");
            }
        };
    }
}
