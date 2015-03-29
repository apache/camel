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
package org.apache.camel.component.raspberry;

import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinAnalogOutput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.RaspiPin;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * The pi4j producer.
 * 
 * @author gautric
 */
public class RaspberryProducer extends DefaultProducer {

    private RaspberryEndpoint endpoint;
    private GpioPin pin;
    private PinAction action;

    /**
     * Create Producer to PIN with OUTPUT mode
     * 
     * @param pi4jEndpoint the endpoint
     * @param pin the pin to manage
     * @param action the action to do
     */
    public RaspberryProducer(RaspberryEndpoint endpoint, GpioPin pin, PinAction action) {
        super(endpoint);

        this.endpoint = endpoint;
        this.pin = pin;
        this.action = action;
    }

    public GpioPin getPin() {
        return pin;
    }

    private void output(Exchange exchange, Object value) {

        switch (pin.getMode()) {

        case DIGITAL_OUTPUT:
            Boolean outputBoolean = exchange.getContext().getTypeConverter().convertTo(Boolean.class, value);
            ((GpioPinDigitalOutput)pin).setState(outputBoolean);
            break;

        case ANALOG_OUTPUT:
            Double outputDouble = exchange.getContext().getTypeConverter().convertTo(Double.class, value);
            ((GpioPinAnalogOutput)pin).setValue(outputDouble);
            break;

        case PWM_OUTPUT:
            Integer outputInt = exchange.getContext().getTypeConverter().convertTo(Integer.class, value);
            ((GpioPinPwmOutput)pin).setPwm(outputInt);

            break;

        case ANALOG_INPUT:
        case DIGITAL_INPUT:
            log.error("Cannot output with INPUT PinMode");
            break;

        default:
            log.error("Any PinMode found");
            break;
        }

    }

    /**
     * Process the message
     */
    public void process(Exchange exchange) throws Exception {
        log.debug(exchange.toString());

        if (action == null) {
            log.trace("No action pick up body");
            this.output(exchange, exchange.getIn().getBody());
        } else {
            log.trace("action= {} ", action);
            switch (action) {

            case TOGGLE:
                if (pin.getMode() == PinMode.DIGITAL_OUTPUT) {
                    ((GpioPinDigitalOutput)pin).toggle();
                }
                break;

            case LOW:
                if (pin.getMode() == PinMode.DIGITAL_OUTPUT) {
                    ((GpioPinDigitalOutput)pin).low();
                }
                break;

            case HIGH:
                if (pin.getMode() == PinMode.DIGITAL_OUTPUT) {
                    ((GpioPinDigitalOutput)pin).high();
                }
                break;

            default:
                log.error("Any action set found");
                break;
            }
        }
    }

    public void setPin(GpioPin pin) {
        this.pin = pin;
    }
}
