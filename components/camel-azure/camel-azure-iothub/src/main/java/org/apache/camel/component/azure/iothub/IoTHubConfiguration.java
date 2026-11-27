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

import java.time.Duration;

import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class IoTHubConfiguration implements Cloneable {

    @UriPath(label = "producer",
             description = "Default device id used when the message does not carry a device id header.")
    private String deviceId;

    @UriParam(label = "security", secret = true,
              description = "The IoT Hub device connection string. Can be overridden per message via the "
                            + "CamelAzureIoTHubConnectionString header.")
    private String connectionString;

    @UriParam(label = "producer", defaultValue = "MQTT",
              description = "The protocol used to connect to Azure IoT Hub.")
    private IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    @UriParam(label = "producer", defaultValue = "TELEMETRY",
              description = "How the message body is delivered: telemetry, device twin reported properties, or both.")
    private IoTHubDeliveryMode deliveryMode = IoTHubDeliveryMode.TELEMETRY;

    @UriParam(label = "producer", defaultValue = "application/json",
              description = "Content type applied to telemetry messages.")
    private String contentType = "application/json";

    @UriParam(label = "producer", defaultValue = "UTF-8",
              description = "Content encoding applied to telemetry messages. Set it to an empty value to send telemetry"
                            + " without a content encoding, for example for a binary body such as Avro or Protobuf.")
    private String contentEncoding = "UTF-8";

    @UriParam(label = "producer", defaultValue = "10s",
              description = "Timeout for telemetry sends, twin reported-property updates, and the subscription to"
                            + " desired properties when a device client is created. Opening the device client uses the"
                            + " retry policy of the Azure IoT SDK and is not limited by this timeout.")
    private Duration sendTimeout = Duration.ofSeconds(10);

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public IotHubClientProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(IotHubClientProtocol protocol) {
        this.protocol = protocol;
    }

    public IoTHubDeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(IoTHubDeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public IoTHubConfiguration copy() {
        try {
            return (IoTHubConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
