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
package org.apache.camel.component.azure.iothub;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Azure IoT Hub component. Sends device telemetry and device-twin reported-property updates to Azure IoT Hub.
 */
@Component("azure-iothub")
public class IoTHubComponent extends DefaultComponent {

    @Metadata
    private IoTHubConfiguration configuration = new IoTHubConfiguration();

    public IoTHubComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final IoTHubConfiguration configuration = this.configuration.copy();

        if (ObjectHelper.isNotEmpty(remaining)) {
            configuration.setDeviceId(remaining);
        }

        final IoTHubEndpoint endpoint = new IoTHubEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public IoTHubConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The shared component configuration.
     */
    public void setConfiguration(IoTHubConfiguration configuration) {
        this.configuration = configuration;
    }
}
