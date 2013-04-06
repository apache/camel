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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * To test timeout.
 *
 * @version 
 */
public class Mina2ExchangeDefaultTimeOutTest extends BaseMina2Test {

    @Test
    public void testDefaultTimeOut() {
        try {
            String result = (String) template.requestBody(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=true", getPort()), "Hello World");
            assertEquals("Okay I will be faster in the future", result);
        } catch (RuntimeCamelException e) {
            fail("Should not get a RuntimeCamelException");
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=true", getPort())).process(new Processor() {

                    public void process(Exchange e) throws Exception {
                        assertEquals("Hello World", e.getIn().getBody(String.class));
                        // MinaProducer has a default timeout of 3 seconds so we just wait 5 seconds
                        // (template.requestBody is a MinaProducer behind the doors)
                        Thread.sleep(1000);

                        e.getOut().setBody("Okay I will be faster in the future");
                    }
                });
            }
        };
    }
}