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
package org.apache.camel.component.hl7;

import ca.uhn.hl7v2.model.Message;
import io.netty.util.ResourceLeakDetector;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

public class HL7MLLPNettyDecoderResourceLeakTest extends HL7TestSupport {

    @BindToRegistry("hl7decoder")
    HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();

    @BindToRegistry("hl7encoder")
    HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();

    @BeforeClass
    // As the ResourceLeakDetector just write error log when it find the leak,
    // We need to check the log file to see if there is a leak.
    public static void enableNettyResourceLeakDetector() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty:tcp://127.0.0.1:" + getPort() + "?decoders=#hl7decoder&encoders=#hl7encoder").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message input = exchange.getIn().getBody(Message.class);
                        exchange.getOut().setBody(input.generateACK());
                    }
                }).to("mock:result");
            }
        };
    }

    @Test
    public void testSendHL7Message() throws Exception {
        String message = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";

        for (int i = 0; i < 10; i++) {
            template.sendBody("netty:tcp://127.0.0.1:" + getPort() + "?decoders=#hl7decoder&encoders=#hl7encoder", message);
        }
    }
}
