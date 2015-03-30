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
package org.apache.camel.component.raspberrypi;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link RaspberryPiEndpoint}.
 */
public class RaspberryPiComponent extends UriEndpointComponent {

    private static final transient Logger LOG = LoggerFactory
            .getLogger(RaspberryPiComponent.class);

    private static final Object SYNC = RaspberryPiComponent.class;

    private GpioController controller;

    public RaspberryPiComponent() {
        super(RaspberryPiEndpoint.class);
    }

    public RaspberryPiComponent(CamelContext context) {
        super(context, RaspberryPiEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining,
            Map<String, Object> parameters) throws Exception {
        RaspberryPiEndpoint endpoint = null;
        Pattern regexPattern = Pattern
                .compile(RaspberryPiConstants.CAMEL_RBPI_URL_PATTERN);

        Matcher match = regexPattern.matcher(remaining);
        if (match.matches()) {

            RaspberryPiType type = getCamelContext().getTypeConverter()
                    .convertTo(RaspberryPiType.class,
                            match.group(RaspberryPiConstants.CAMEL_URL_TYPE));

            switch (type) {
            case PIN:
                endpoint = new RaspberryPiEndpoint(uri, remaining, this, controller);
                parameters.put(RaspberryPiConstants.CAMEL_URL_ID,
                        match.group(RaspberryPiConstants.CAMEL_URL_ID));
                parameters.put(RaspberryPiConstants.CAMEL_URL_TYPE, type);
                setProperties(endpoint, parameters);

                break;

            default:
                break;
            }
        }

        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        synchronized (SYNC) {
            if (controller == null) {
                controller = GpioFactory.getInstance();
            }
        }
    }

    public GpioController getController() {
        return controller;
    }

    public void setController(GpioController controller) {
        this.controller = controller;
    }

}
