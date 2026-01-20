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
package org.apache.camel.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class StreamCachingChoiceTest extends ContextTestSupport {

    @Test
    public void testStreamCaching() throws Exception {
        getMockEndpoint("mock:1").expectedMessageCount(0);
        getMockEndpoint("mock:2").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        MyInputStream bos = new MyInputStream("2".getBytes());

        template.sendBody("direct:start", bos);

        assertMockEndpointsSatisfied();
    }

    private class MyInputStream extends InputStream {

        private final ByteArrayInputStream bos;

        public MyInputStream(byte[] buf) {
            this.bos = new ByteArrayInputStream(buf);
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public int read() throws IOException {
            return bos.read();
        }

        @Override
        public synchronized void reset() {
            bos.reset();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .choice()
                            .when().simple("${bodyAs(String)} == '1'")
                                .to("mock:1")
                            .when().simple("${bodyAs(String)} == '2'")
                                .to("mock:2")
                            .otherwise()
                                .to("mock:other")
                        .end();
            }
        };
    }
}
