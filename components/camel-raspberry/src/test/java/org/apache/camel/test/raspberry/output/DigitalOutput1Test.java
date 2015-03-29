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
package org.apache.camel.test.raspberry.output;

import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.raspberry.mock.RaspiGpioProviderMock;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class DigitalOutput1Test extends CamelTestSupport {

    public static final RaspiGpioProviderMock MOCK_RASPI = new RaspiGpioProviderMock();

    static {
        // Mandatory we are not inside a Real Raspberry PI
        GpioFactory.setDefaultProvider(MOCK_RASPI);
    }

    @Test
    public void consumeAnalogEvent() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        Assert.assertEquals("", PinState.HIGH, MOCK_RASPI.getState(RaspiPin.GPIO_07));
        Assert.assertEquals("", PinState.LOW, MOCK_RASPI.getState(RaspiPin.GPIO_08));
        Assert.assertEquals("", PinState.HIGH, MOCK_RASPI.getState(RaspiPin.GPIO_09));

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("timer://foo?repeatCount=1").id("rbpi-route").to("log:org.apache.camel.component.raspberry?showAll=true&multiline=true")
                    .to("rbpi://pin?id=7&mode=output&state=LOW&action=TOGGLE").to("rbpi://pin?id=8&mode=output&state=HIGH&action=LOW")
                    .to("rbpi://pin?id=9&mode=output&state=LOW&action=HIGH").to("mock:result");

            }
        };
    }
}
