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
package org.apache.camel.component.jt400;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test case for routes that contain <code>jt400:</code> endpoints This test
 * case does nothing by default -- you can use it to test integration when there
 * is a real AS/400 system available by filling in correct values for
 * {@link #USER}, {@link #PASSWORD}, {@link #SYSTEM}, {@link #LIBRARY} and
 * {@link #QUEUE}
 */
public class Jt400RouteTest extends CamelTestSupport {

    // fill in correct values for all constants to test with a real AS/400
    // system
    private static final String USER = "username";
    private static final String PASSWORD = "password";
    private static final String SYSTEM = null;
    private static final String LIBRARY = "library";
    private static final String QUEUE = "queue";

    @Test
    public void testBasicTest() throws Exception {
        if (SYSTEM != null) {
            MockEndpoint endpoint = getMockEndpoint("mock:a");
            endpoint.expectedBodiesReceived("Test message");
            sendBody("direct:a", "Test message");
            endpoint.assertIsSatisfied();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                if (SYSTEM != null) {
                    String uri = String.format("jt400://%s:%s@%s/QSYS.LIB/%s.LIB/%s.DTAQ", USER, PASSWORD,
                            SYSTEM, LIBRARY, QUEUE);
                    from("direct:a").to(uri);
                    from(uri).to("mock:a");
                }
            }
        };
    }
}
