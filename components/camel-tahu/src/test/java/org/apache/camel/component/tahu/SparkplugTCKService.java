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
package org.apache.camel.component.tahu;

import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.test.infra.common.services.TestService;
import org.apache.camel.test.infra.core.MockUtils;
import org.apache.camel.test.infra.hivemq.common.HiveMQProperties;
import org.apache.camel.test.infra.hivemq.services.HiveMQService;
import org.apache.camel.test.infra.hivemq.services.HiveMQServiceFactory;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit6.TestSupport.bodyAs;

public final class SparkplugTCKService implements TestService {

    public static final String SPARKPLUG_TCK_TEST_CONTROL_TOPIC = "SPARKPLUG_TCK/TEST_CONTROL";
    public static final String SPARKPLUG_TCK_LOG_TOPIC = "SPARKPLUG_TCK/LOG";
    public static final String SPARKPLUG_TCK_RESULT_TOPIC = "SPARKPLUG_TCK/RESULT";

    @RegisterExtension
    static HiveMQService hiveMQService
            = HiveMQServiceFactory.createSingletonService(HiveMQProperties.HIVEMQ_SPARKPLUG_INSTANCE_SELECTOR);

    private static MqttClient mqttClient;

    public static String getMqttHostAddress() {
        if (!hiveMQService.isRunning()) {
            hiveMQService.initialize();
        }

        return hiveMQService.getMqttHostAddress();
    }

    private CamelContext monitorCamelContext;

    public MockEndpoint spTckLogMockEndpoint;
    public MockEndpoint spTckResultMockEndpoint;

    public NotifyBuilder spTckResultMockNotify;
    public NotifyBuilder spTckLogMockNotify;

    private void startClient() {
        try {
            mqttClient = new MqttClient(
                    getMqttHostAddress(), "Tahu-Test-" + MqttClient.generateClientId(), new MemoryPersistence());

            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            if (hiveMQService.getUserName() != null) {
                mqttConnectOptions.setUserName(hiveMQService.getUserName());
                mqttConnectOptions.setPassword(hiveMQService.getUserPassword());
            }

            mqttClient.connect(mqttConnectOptions);

        } catch (MqttException e) {
            throw new RuntimeCamelException("Exception caught connecting MQTT test client", e);
        }
    }

    private void startMonitorCamelContext() {
        try {
            monitorCamelContext = new DefaultCamelContext();

            spTckLogMockEndpoint = MockUtils.getMockEndpoint(monitorCamelContext,
                    "mock:" + SPARKPLUG_TCK_LOG_TOPIC + "?log=true", true);
            spTckResultMockEndpoint = MockUtils.getMockEndpoint(monitorCamelContext,
                    "mock:" + SPARKPLUG_TCK_RESULT_TOPIC + "?log=true", true);

            monitorCamelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    fromF("direct:" + SPARKPLUG_TCK_LOG_TOPIC).to(spTckLogMockEndpoint);
                    fromF("direct:" + SPARKPLUG_TCK_RESULT_TOPIC).to(spTckResultMockEndpoint);
                }
            });
            monitorCamelContext.start();

        } catch (Exception e) {
            throw new RuntimeCamelException("Exception caught configuring Camel monitor context and routes", e);
        }
    }

    private void startSubscriptions() {
        final SparkplugTCKMessageListener spTckMessageListener = new SparkplugTCKMessageListener();

        try {
            mqttClient.subscribe(SPARKPLUG_TCK_RESULT_TOPIC, spTckMessageListener);
            mqttClient.subscribe(SPARKPLUG_TCK_LOG_TOPIC, spTckMessageListener);
        } catch (MqttException e) {
            throw new RuntimeCamelException("Exception caught subscribing to TCK topics", e);
        }

    }

    public void initiateTckTest(String testConfig) throws MqttException {
        sendTestControlMessage("NEW_TEST " + testConfig);

        spTckResultMockEndpoint = MockUtils.getMockEndpoint(monitorCamelContext, "mock:" + SPARKPLUG_TCK_RESULT_TOPIC, false);
        spTckLogMockEndpoint = MockUtils.getMockEndpoint(monitorCamelContext, "mock:" + SPARKPLUG_TCK_LOG_TOPIC, false);

        // Result message expectations are always the same--one message saying the test passed
        spTckResultMockEndpoint.expectedMessageCount(1);
        spTckResultMockEndpoint.message(0).body(String.class).contains("OVERALL: PASS");

        spTckResultMockNotify = new NotifyBuilder(monitorCamelContext)
                .from("direct:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC)
                .whenCompleted(1)
                .create();

        spTckLogMockNotify = new NotifyBuilder(monitorCamelContext)
                .from("direct:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC)
                .filter(PredicateBuilder.and(
                        // Filter the expected log messages--anything else causes the TCK test to fail
                        bodyAs(String.class).not().contains("Creating simulated host application"),
                        bodyAs(String.class).not().contains("Waiting for the Edge and Device to come online"),
                        bodyAs(String.class).not().contains("Edge Send Complex Data"),
                        bodyAs(String.class).not().contains("Host Application is online, so using that")))
                .whenCompleted(1)
                .create();
    }

    public void resetTckTest() throws MqttException {
        sendTestControlMessage("END_TEST");

        MockEndpoint.resetMocks(monitorCamelContext);
    }

    private void sendTestControlMessage(String message) throws MqttException {
        mqttClient.publish(SPARKPLUG_TCK_TEST_CONTROL_TOPIC, new MqttMessage(message.getBytes(StandardCharsets.UTF_8)));
    }

    private class SparkplugTCKMessageListener implements IMqttMessageListener {
        private final ProducerTemplate producerTemplate;

        private SparkplugTCKMessageListener() {
            producerTemplate = monitorCamelContext.createProducerTemplate();
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

            producerTemplate.sendBody("direct:" + topic, payload);
        }
    }

    boolean isConnected() {
        return mqttClient.isConnected();
    }

    @Override
    public void initialize() {
        startClient();
        startMonitorCamelContext();
        startSubscriptions();
    }

    @Override
    public void shutdown() {
        monitorCamelContext.stop();

        try {
            mqttClient.disconnect();
            mqttClient.close();
        } catch (MqttException e) {
            throw new RuntimeCamelException("Exception caught disconnecting MQTT test client", e);
        }
    }

    @Override
    public void registerProperties() {
    }
}
