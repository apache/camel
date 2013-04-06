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
package org.apache.camel.component.jt400;

import java.util.Arrays;

import org.apache.camel.Exchange;
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
public class Jt400PgmRouteTest extends CamelTestSupport {

    // fill in correct values for all constants to test with a real AS/400
    // system
    private static final String USER = "grupo";
    private static final String PASSWORD = "atwork";
    private static final String SYSTEM = null;
    private static final String LIBRARY = "library";
    private static final String PGM = "program";
    private static final String FIELDS_LENGTH = "1,512,2";
    private static final String OUTPUT_FIELDS = "1,2";

    @Test
    public void testBasicTest() throws Exception {
        if (SYSTEM != null) {
            final MockEndpoint endpoint = getMockEndpoint("mock:a");
            endpoint.setExpectedMessageCount(1);
            Runnable runnable = new Runnable() {

                public void run() {
                    Exchange exchange = endpoint.getReceivedExchanges().get(0);
                    char[] secondParameter = new char[512];
                    Arrays.fill(secondParameter, ' ');
                    String[] expectedBody = new String[]{"1234", new String(secondParameter), "01"};
                    Object actualBody = exchange.getIn().getBody();

                    assertNotNull(actualBody);
                    assertTrue(actualBody.getClass().isArray());

                    String[] actualBodyTyped = (String[]) actualBody;
                    for (int i = 0; i < expectedBody.length; i++) {
                        assertEquals(expectedBody[i], actualBodyTyped[i]);
                    }
                }
            };
            endpoint.expects(runnable);
            sendBody("direct:a", new String[]{"1234", "", ""});
            endpoint.assertIsSatisfied();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                if (SYSTEM != null) {
                    String uri = String
                            .format("jt400://%s:%s@%s/QSYS.LIB/%s.LIB/%s.pgm?outputFieldsIdx=%s&fieldsLength=%s",
                                    USER, PASSWORD, SYSTEM, LIBRARY, PGM, OUTPUT_FIELDS, FIELDS_LENGTH);
                    from("direct:a").to(uri).to("mock:a");
                }
            }
        };
    }
}
