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
package org.apache.camel.component.pubnub.example;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pubnub.PubNubConstants;
import org.apache.camel.main.Main;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        private final String deviceEP = "pubnub://pubsub:iot?uuid=device2&publisherKey=" + PubNubExampleConstants.PUBNUB_PUBLISHER_KEY + "&subscriberKey="
                                        + PubNubExampleConstants.PUBNUB_SUBSCRIBER_KEY;
        private final String devicePrivateEP = "pubnub://pubsub:device2private?uuid=device2&publisherKey=" + PubNubExampleConstants.PUBNUB_PUBLISHER_KEY + "&subscriberKey="
                                               + PubNubExampleConstants.PUBNUB_SUBSCRIBER_KEY;

        @Override
        public void configure() throws Exception {
            //@formatter:off
            from("timer:device2").routeId("device-event-route")
                .bean(PubNubSensor2Example.EventGeneratorBean.class, "getRandomEvent('device2')")
                .convertBodyTo(JSONObject.class)
                .to(deviceEP);
            
            from(devicePrivateEP)
                .routeId("device-unicast-route")
                .log("Message from master to device2 : ${body}");
            //@formatter:on
        }
    }

    static class PubsubRoute extends RouteBuilder {
        private static String masterEP = "pubnub://pubsub:iot?uuid=master&subscriberKey=" + PubNubExampleConstants.PUBNUB_SUBSCRIBER_KEY + "&publisherKey="
                                         + PubNubExampleConstants.PUBNUB_PUBLISHER_KEY;
        private static Map<String, String> devices = new ConcurrentHashMap<String, String>();

        @Override
        public void configure() throws Exception {
            //@formatter:off
            from(masterEP)
                .routeId("master-route")
                .convertBodyTo(JSONObject.class)
                .bean(PubNubSensor2Example.PubsubRoute.DataProcessorBean.class, "doSomethingInteresting(${body})")
                .log("${body} headers : ${headers}").to("mock:result");
            
            //TODO Could remote control device to turn on/off sensor measurement 
            from("timer:master?delay=15s&period=5s").routeId("unicast2device-route")
                .setHeader(PubNubConstants.CHANNEL, method(PubNubSensor2Example.PubsubRoute.DataProcessorBean.class, "getUnicastChannelOfDevice()"))
                .setBody(constant("Hello device"))
                .to(masterEP);
            //@formatter:on
        }

        static class DataProcessorBean {
            @EndpointInject(uri = "pubnub://pubsub:iot?uuid=master&subscriberKey=" + PubNubExampleConstants.PUBNUB_SUBSCRIBER_KEY)
            private static ProducerTemplate template;

            public static String getUnicastChannelOfDevice() {
                // just get the first channel
                return devices.values().iterator().next();
            }

            public static void doSomethingInteresting(JSONObject message) {
                String deviceUUID;
                try {
                    deviceUUID = message.getString("uuid");
                    if (devices.get(deviceUUID) == null) {
                        Map<String, Object> headers = new HashMap<String, Object>();
                        headers.put(PubNubConstants.OPERATION, "WHERE_NOW");
                        headers.put(PubNubConstants.UUID, deviceUUID);
                        JSONObject response = (JSONObject)template.requestBodyAndHeaders(null, headers);
                        JSONArray listofDeviceChannels = response.getJSONArray("channels");
                        devices.put(deviceUUID, listofDeviceChannels.getString(0));
                    }
                } catch (JSONException e) {
                }
            }
        }
    }

    public static class EventGeneratorBean {
        public static String getRandomEvent(String device) throws JSONException {
            Random rand = new Random();
            String s = "{uuid:" + device + ", humidity:" + rand.nextInt(100) + ", temperature:" + rand.nextInt(40) + "}";
            return s;
        }
    }
}
