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
package org.apache.camel.component.cxf.jaxws;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfConsumerWithTryCatchTest extends CxfConsumerTest {

    private static final String ECHO_OPERATION = "echo";
    private static final String ECHO_BOOLEAN_OPERATION = "echoBoolean";

    // START SNIPPET: example
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SIMPLE_ENDPOINT_URI).choice().when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_OPERATION))
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                Message in = exchange.getIn();
                                // Get the parameter list
                                List<?> parameter = in.getBody(List.class);
                                // Get the operation name
                                String operation = (String) in.getHeader(CxfConstants.OPERATION_NAME);
                                Object result = operation + " " + (String) parameter.get(0);
                                // Put the result back
                                exchange.getMessage().setBody(result);
                            }
                        })
                        .when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_BOOLEAN_OPERATION))
                        .doTry()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new IllegalStateException();
                            }
                        })
                        .doCatch(IllegalStateException.class).process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Message in = exchange.getIn();
                                // Get the parameter list
                                List<?> parameter = in.getBody(List.class);
                                // Put the result back
                                exchange.getMessage().setBody(parameter.get(0));
                            }
                        })
                        .end();
            }
        };
    }

    @Override
    @Disabled("The test from the parent class is not applicable in this scenario")
    public void testXmlDeclaration() {
        // do nothing here
    }

}
