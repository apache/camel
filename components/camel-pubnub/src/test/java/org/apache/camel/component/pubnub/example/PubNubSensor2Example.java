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
package org.apache.camel.component.pubnub.example;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pubnub.PubNubConstants;
import org.apache.camel.main.Main;

import static org.apache.camel.component.pubnub.PubNubConstants.OPERATION;
import static org.apache.camel.component.pubnub.PubNubConstants.UUID;
import static org.apache.camel.component.pubnub.example.PubNubExampleConstants.PUBNUB_PUBLISH_KEY;
import static org.apache.camel.component.pubnub.example.PubNubExampleConstants.PUBNUB_SUBSCRIBE_KEY;

public final class PubNubSensor2Example {

    private PubNubSensor2Example() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new PubsubRoute());
        main.addRouteBuilder(new SimulatedDeviceEventGeneratorRoute());
        main.run();
    }

    static class SimulatedDeviceEventGeneratorRoute extends RouteBuilder {
        private final String deviceEP = "pubnub:iot?uuid=device2&publishKey=" + PUBNUB_PUBLISH_KEY + "&subscribeKey=" + PUBNUB_SUBSCRIBE_KEY;
        private final String devicePrivateEP = "pubnub:device2private?uuid=device2&publishKey=" + PUBNUB_PUBLISH_KEY + "&subscribeKey=" + PUBNUB_SUBSCRIBE_KEY;

        @Override
        public void configure() throws Exception {
            from("timer:device2").routeId("device-event-route")
                .bean(PubNubSensor2Example.EventGeneratorBean.class, "getRandomEvent('device2')")
                .to(deviceEP);
            
            from(devicePrivateEP)
                .routeId("device-unicast-route")
                .log("Message from master to device2 : ${body}");
        }
    }

    static class PubsubRoute extends RouteBuilder {
        private static String masterEP = "pubnub:iot?uuid=master&subscribeKey=" + PUBNUB_SUBSCRIBE_KEY + "&publishKey=" + PUBNUB_PUBLISH_KEY;
        private static Map<String, String> devices = new ConcurrentHashMap<>();

        @Override
        public void configure() throws Exception {
            from(masterEP)
                .routeId("master-route")
                .bean(PubNubSensor2Example.PubsubRoute.DataProcessorBean.class, "doSomethingInteresting(${body})")
                .log("${body} headers : ${headers}").to("mock:result");
            
            //TODO Could remote control device to turn on/off sensor measurement 
            from("timer:master?delay=15000&period=5000").routeId("unicast2device-route")
                .setHeader(PubNubConstants.CHANNEL, method(PubNubSensor2Example.PubsubRoute.DataProcessorBean.class, "getUnicastChannelOfDevice()"))
                .setBody(constant("Hello device"))
                .to(masterEP);
        }

        public static class DataProcessorBean {
            @EndpointInject("pubnub:iot?uuid=master&subscribeKey=" + PUBNUB_SUBSCRIBE_KEY)
            private static ProducerTemplate template;

            public static String getUnicastChannelOfDevice() {
                // just get the first channel
                return devices.values().iterator().next();
            }

            public static void doSomethingInteresting(PNMessageResult message) {
                String deviceUUID;
                deviceUUID = message.getPublisher();
                if (devices.get(deviceUUID) == null) {
                    Map<String, Object> headers = new HashMap<>();
                    headers.put(OPERATION, "WHERENOW");
                    headers.put(UUID, deviceUUID);
                    @SuppressWarnings("unchecked")
                    java.util.List<String> channels = (java.util.List<String>) template.requestBodyAndHeaders(null, headers);
                    devices.put(deviceUUID, channels.get(0));
                }
            }
        }
    }

    static class DeviceWeatherInfo {
        private String device;
        private int humidity;
        private int temperature;

        DeviceWeatherInfo(String device) {
            Random rand = new Random();
            this.device = device;
            this.humidity = rand.nextInt(100);
            this.temperature = rand.nextInt(40);
        }

        public String getDevice() {
            return device;
        }

        public int getHumidity() {
            return humidity;
        }

        public int getTemperature() {
            return temperature;
        }

    }

    public static class EventGeneratorBean {
        public static DeviceWeatherInfo getRandomEvent(String device) {
            return new DeviceWeatherInfo(device);
        }
    }
}
