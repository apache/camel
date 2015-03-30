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

import java.lang.reflect.Field;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a pi4j endpoint.
 * 
 */
@UriEndpoint(scheme = "rbpi", syntax = "rbpi:id", consumerClass = RaspberryConsumer.class, label = "device,IoT", title = "RaspberryPi")
public class RaspberryEndpoint extends DefaultEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(RaspberryEndpoint.class);

    @UriPath(description = "PIN ID Wiring")
    @Metadata(required = "true")
    private String id;

    @UriParam(defaultValue = "LOW", description = "Digital Only: if input mode then state trigger event, if output then start value")
    private PinState state = PinState.LOW;

    @UriParam(defaultValue = "output", enums = "output:input:pwm_output:analog_ouput:analog_input")
    @Metadata(required = "true")
    private PinMode mode = PinMode.DIGITAL_OUTPUT;

    @UriParam(description = "Default : use Body to if Action for ouput Pin (TOGGLE, BUZZ, HIGH, LOW for digital only) (HEADER digital and analog) ", enums = "TOGGLE:BUZZ:HIGH:LOW:HEADER")
    private PinAction action;

    @UriParam(defaultValue = "0", description = "Analog or PWN Only")
    private double value;

    @UriParam(defaultValue = "true", description = "pin shutdown behavior")
    private boolean shutdownExport = true;

    @UriParam(defaultValue = "LOW", description = "pin state value before exit program")
    private PinState shutdownState = PinState.LOW;

    @UriParam(defaultValue = "OFF", description = "pin resistance before exit program")
    private PinPullResistance shutdownResistance = PinPullResistance.OFF;

    @UriParam(defaultValue = "PULL_UP")
    private PinPullResistance pullResistance = PinPullResistance.PULL_UP;

    @UriParam(defaultValue = "pin", enums = "pin:i2c:spi:serial", description = "pin : GPIO Wiringpi, i2c:spi:serial not yet available")
    @Metadata(required = "true")
    private String type = "pin";

    private GpioController controller;

    public RaspberryEndpoint() {
    }

    public RaspberryEndpoint(String uri, String pin, RaspberryComponent component, GpioController crtl) {
        super(uri, component);
        ObjectHelper.notNull(crtl, "controller");
        this.controller = crtl;
    }

    /**
     * Create consumer map to an Input PIN
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.debug(this.toString());

        ObjectHelper.notNull(this.mode, "mode");
        GpioPin pin = isAlreadyProvisioned();

        if (pin == null) {
            switch (this.mode) {
            case DIGITAL_INPUT:
                pin = getOrCreateController().provisionDigitalInputPin(getPin(), pullResistance);
                break;
            case ANALOG_INPUT:
                pin = getOrCreateController().provisionAnalogInputPin(getPin());
                break;
            case ANALOG_OUTPUT:
            case DIGITAL_OUTPUT: // PinMode.allOutput()
            case PWM_OUTPUT:
                LOG.error("Cannot create Consumer with OUTPUT Mode");
                return null;
            default:
                LOG.error("Cannot create Consumer w/o Mode");
                break;
            }

        } else {
            throw new IllegalArgumentException("Cannot create twice same input pin for Consumer");
        }

        return new RaspberryConsumer(this, processor, pin, state);
    }

    /**
     * Create producer map to an Output PIN
     */
    public Producer createProducer() throws Exception {
        LOG.debug(this.toString());
        ObjectHelper.notNull(this.mode, "mode");
        GpioPin pin = isAlreadyProvisioned();
        if (pin == null) {
            switch (this.mode) {
            case DIGITAL_OUTPUT:
                pin = getOrCreateController().provisionDigitalOutputPin(getPin(), state);
                break;
            case ANALOG_OUTPUT:
                pin = getOrCreateController().provisionAnalogOutputPin(getPin(), value);
                break;
            case PWM_OUTPUT:
                pin = getOrCreateController().provisionPwmOutputPin(getPin(), (int)value);
                break;
            case ANALOG_INPUT: // PinMode.allInput()
            case DIGITAL_INPUT:
                LOG.error("Cannot create Producer with INPUT Mode");
                return null;
            default:
                LOG.error("Cannot create Producer w/o Mode");
                break;
            }
            pin.setMode(this.mode); // Force Mode to avoid NPE
        } else {
            throw new IllegalArgumentException("Cannot create twice same output pin for Producer");
        }

        // shutdownOption(pin);
        return new RaspberryProducer(this, pin, action);
    }

    public PinAction getAction() {
        return action;
    }

    public GpioController getController() {
        return controller;
    }

    public String getId() {
        return id;
    }

    public PinMode getMode() {
        return mode;
    }

    public GpioController getOrCreateController() {
        return controller;
    }

    private Pin getPin() {

        if (LOG.isDebugEnabled()) {
            LOG.debug(" Pin Id > " + id);
        }

        Pin ret = null;
        String pinAddress = (Integer.parseInt(id) < 10) ? "0" + id : id;

        Class<RaspiPin> clazz = RaspiPin.class;

        try {

            Field clazzField = clazz.getField("GPIO_" + pinAddress);
            ret = (Pin)clazzField.get(null);

        } catch (NoSuchFieldException e) {
            LOG.debug("", e);
        } catch (SecurityException e) {
            LOG.debug("", e);
        } catch (IllegalArgumentException e) {
            LOG.debug("", e);
        } catch (IllegalAccessException e) {
            LOG.debug("", e);
        }

        return ret;
    }

    public PinPullResistance getPullResistance() {
        return pullResistance;
    }

    public boolean getShutdownExport() {
        return shutdownExport;
    }

    public PinPullResistance getShutdownResistance() {
        return shutdownResistance;
    }

    public PinState getShutdownState() {
        return shutdownState;
    }

    public PinState getState() {
        return state;
    }

    public String getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    private GpioPin isAlreadyProvisioned() {
        GpioPin ret = null;

        for (GpioPin pin : getOrCreateController().getProvisionedPins()) {
            if (pin.getPin().getAddress() == Integer.parseInt(id)) {
                ret = pin;
                break;
            }
        }
        return ret;
    }

    public boolean isSingleton() {
        return true;
    }

    public void setAction(PinAction action) {
        this.action = action;
    }

    public void setController(GpioController controller) {
        this.controller = controller;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setMode(PinMode mode) {
        this.mode = mode;
    }

    public void setPullResistance(PinPullResistance pullResistance) {
        this.pullResistance = pullResistance;
    }

    public void setShutdownExport(boolean shutdownExport) {
        this.shutdownExport = shutdownExport;
    }

    public void setShutdownResistance(PinPullResistance shutdownResistance) {
        this.shutdownResistance = shutdownResistance;
    }

    public void setShutdownState(PinState shutdownState) {
        this.shutdownState = shutdownState;
    }

    public void setState(PinState state) {
        this.state = state;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setValue(double value) {
        this.value = value;
    }

    private void shutdownOption(GpioPin pin) {
        pin.setShutdownOptions(shutdownExport, shutdownState, shutdownResistance);
    }

}
