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

import java.util.Map;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link RaspberryEndpoint}.
 */
public class RaspberryComponent extends UriEndpointComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(RaspberryComponent.class);

    private static final Object SYNC = RaspberryComponent.class;

    public RaspberryComponent() {
        super(RaspberryEndpoint.class);
    }

    public RaspberryComponent(CamelContext context) {
        super(context, RaspberryEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = null;
        GpioController gpio = null;

        synchronized (SYNC) { // Retrieve Factory
            gpio = GpioFactory.getInstance();
        }

        if (RaspberryConstants.TYPE_ENDPOINT_PIN.compareTo(remaining) == 0) {
            endpoint = new RaspberryEndpoint(uri, remaining, this, gpio);
            setProperties(endpoint, parameters);
        }

        return endpoint;
    }
}
