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

package org.apache.camel.component.tahu.handlers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.component.tahu.SparkplugTCKService;
import org.apache.camel.component.tahu.TahuConstants;
import org.eclipse.tahu.edge.CommandListener;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled(
        "Manual test for underlying TahuHostApplication implementation complies with Sparkplug B TCK for Host Applications")
@SuppressWarnings("unused")
public class TahuHostApplicationManualIT {

    private static final Logger LOG = LoggerFactory.getLogger(TahuHostApplicationManualIT.class);

    @RegisterExtension
    public static SparkplugTCKService spTckService = new SparkplugTCKService();

    private static final String COMMAND_LISTENER_DIRECTORY = "/tmp/commands";
    private static final long COMMAND_LISTENER_POLL_RATE = 50L;

    private static final String HOST_ID = "IamHost";
    private static final String MQTT_SERVER_NAME_1 = "Mqtt Server One";
    private static final String MQTT_CLIENT_ID_1 = "Tahu_Host_Application";
    private static final String MQTT_SERVER_URL_1 = "tcp://localhost:1883";
    private static final String USERNAME_1 = "admin";
    private static final String PASSWORD_1 = "changeme";
    private static final String MQTT_SERVER_NAME_2 = "Mqtt Server Two";
    private static final String MQTT_CLIENT_ID_2 = "Tahu_Host_Application";
    private static final String MQTT_SERVER_URL_2 = "tcp://localhost:1884";
    private static final String USERNAME_2 = null;
    private static final String PASSWORD_2 = null;
    private static final int KEEP_ALIVE_TIMEOUT = 30;

    private static final List<MqttServerDefinition> mqttServerDefinitions = new ArrayList<>();

    private CommandListener commandListener;
    private TahuHostApplication hostApplication;

    @BeforeAll
    public static void beforeAll() throws Exception {
        mqttServerDefinitions.add(new MqttServerDefinition(
                new MqttServerName(MQTT_SERVER_NAME_1),
                new MqttClientId(MQTT_CLIENT_ID_1, false),
                new MqttServerUrl(MQTT_SERVER_URL_1),
                USERNAME_1,
                PASSWORD_1,
                KEEP_ALIVE_TIMEOUT,
                null));
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        hostApplication = new TahuHostApplication.HostApplicationBuilder()
                .hostId(HOST_ID)
                .serverDefinitions(mqttServerDefinitions)
                .onMessageConsumer(this::onMessageConsumer)
                .onMetricConsumer(this::onMetricConsumer)
                .build();
    }

    @AfterEach
    public void afterEach() throws Exception {
        spTckService.resetTckTest();

        hostApplication.shutdown();
    }

    static Stream<Arguments> handlerTestArgsProvider() {
        return Stream.of(
                Arguments.of("host SessionEstablishmentTest " + HOST_ID, false, 0),
                Arguments.of("host SessionTerminationTest " + MQTT_CLIENT_ID_1, true, 3),
                Arguments.of("host SendCommandTest G2 E2 D2", true, 15),
                Arguments.of("host EdgeSessionTerminationTest G3 E3 D3", true, 15),
                Arguments.of("host MessageOrderingTest G4 E4 D4 5000", true, 15));
    }

    @ParameterizedTest
    @MethodSource("handlerTestArgsProvider")
    public void handlerTest(String testParams, boolean startHandlerBeforeTCKInitiate, int maxWaitForResultsCount)
            throws Exception {
        LOG.info("handlerTest: Starting {}", testParams);

        if (startHandlerBeforeTCKInitiate) {
            hostApplication.startup();
        }

        initiateTckTest(testParams);

        if (!startHandlerBeforeTCKInitiate) {
            hostApplication.startup();
        }

        Instant timeout = Instant.now().plusSeconds(15L);

        int waitForResultsCount = 0;
        while (waitForResultsCount < maxWaitForResultsCount && Instant.now().isBefore(timeout)) {

            waitForResultsCount += 1;

            if (spTckService.spTckResultMockNotify.matches(1L, TimeUnit.SECONDS)) {
                break;
            }
        }

        if (testParams.startsWith("host SessionTerminationTest")) {
            hostApplication.shutdown();
        }

        spTckService.spTckResultMockEndpoint.assertIsSatisfied();
    }

    private void initiateTckTest(String testParams) throws Exception {
        spTckService.initiateTckTest(testParams);
    }

    private static final List<MessageType> HANDLED_MESSAGE_TYPES = List.of(
            MessageType.NBIRTH,
            MessageType.NDATA,
            MessageType.NDEATH,
            MessageType.DBIRTH,
            MessageType.DDATA,
            MessageType.DDEATH);

    void onMessageConsumer(EdgeNodeDescriptor edgeNodeDescriptor, org.eclipse.tahu.message.model.Message tahuMessage) {
        try {
            Topic topic = tahuMessage.getTopic();
            SparkplugBPayload payload = tahuMessage.getPayload();

            if (HANDLED_MESSAGE_TYPES.contains(topic.getType())) {

                if (payload.getTimestamp() != null) {}

                if (payload.getSeq() != null) {}

                if (payload.getUuid() != null) {}

                if (payload.getBody() != null) {}

                Map<String, Object> payloadMetrics = payload.getMetrics().stream()
                        .map(m -> new Object[] {TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m})
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                if (!payloadMetrics.isEmpty()) {}

            } else {
                LOG.warn(
                        "TahuHostAppConsumer onMessageConsumer: Unknown Message Type {} from {} - ignoring",
                        topic.getType(),
                        edgeNodeDescriptor);
            }

        } catch (Exception e) {
            LOG.debug("Exception caught processing exchange from Sparkplug Message", e);
        }
    }

    void onMetricConsumer(EdgeNodeDescriptor edgeNodeDescriptor, Metric metric) {}
}
