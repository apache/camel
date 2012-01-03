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
package org.apache.camel.component.mina2;

import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test with InOut however we want sometimes to not send a response.
 */
public class Mina2InOutWithForcedNoResponseTest extends BaseMina2Test {

    int port1;
    int port2;

    @Test
    public void testResponse() throws Exception {
        Object out = template.requestBody("mina2:tcp://localhost:" + port1 + "?sync=true", "Woodbine");
        assertEquals("Hello Chad", out);
    }

    @Test
    public void testNoResponseDisconnectOnNoReplyFalse() throws Exception {
        try {
            template.requestBody("mina2:tcp://localhost:" + port2 + "?sync=true&timeout=100", "London");
            Thread.sleep(1000);
            fail("Should throw an exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                from("mina2:tcp://localhost:" + port1 + "?sync=true")
                    .choice()
                        .when(body().isEqualTo("Woodbine"))
                            .transform(constant("Hello Chad"))
                        .otherwise()
                            .transform(constant(null));

                from("mina2:tcp://localhost:" + port2 + "?sync=true&disconnectOnNoReply=false&noReplyLogLevel=OFF").
                    choice()
                        .when(body().isEqualTo("Woodbine"))
                            .transform(constant("Hello Chad")).
                        otherwise()
                            .transform(constant(null));
            }
        };
    }
}