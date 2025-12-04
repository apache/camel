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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.component.tahu.SparkplugTCKService;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.message.model.SparkplugMeta;
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
        "Manual test for underlying TahuEdgeClient implementation complies with Sparkplug B TCK for Edge Nodes and Devices")
@SuppressWarnings("unused")
public class TahuEdgeClientManualIT {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeClientManualIT.class);

    @RegisterExtension
    public static SparkplugTCKService spTckService = new SparkplugTCKService();

    private static final String GROUP_ID = "G2";
    private static final String EDGE_NODE_ID = "E2";
    private static final EdgeNodeDescriptor EDGE_NODE_DESCRIPTOR = new EdgeNodeDescriptor(GROUP_ID, EDGE_NODE_ID);
    private static final List<String> DEVICE_IDS = Arrays.asList("D2");
    private static final List<DeviceDescriptor> DEVICE_DESCRIPTORS =
            Arrays.asList(new DeviceDescriptor(EDGE_NODE_DESCRIPTOR, "D2"));
    private static final String PRIMARY_HOST_ID = "IamHost";
    private static final boolean USE_ALIASES = false;
    private static final Long REBIRTH_DEBOUNCE_DELAY = 5000L;

    private static final MqttServerName MQTT_SERVER_NAME_1 = new MqttServerName("Mqtt Server One");
    private static final String MQTT_CLIENT_ID_1 = "Sparkplug-Tahu-Compatible-Impl-One";
    private static final MqttServerUrl MQTT_SERVER_URL_1 =
            MqttServerUrl.getMqttServerUrlSafe(SparkplugTCKService.getMqttHostAddress());
    private static final String USERNAME_1 = "admin";
    private static final String PASSWORD_1 = "changeme";
    private static final MqttServerName MQTT_SERVER_NAME_2 = new MqttServerName("Mqtt Server Two");
    private static final String MQTT_CLIENT_ID_2 = "Sparkplug-Tahu-Compatible-Impl-Two";
    private static final MqttServerUrl MQTT_SERVER_URL_2 = MqttServerUrl.getMqttServerUrlSafe("tcp://localhost:1884");
    private static final String USERNAME_2 = "admin";
    private static final String PASSWORD_2 = "changeme";
    private static final int KEEP_ALIVE_TIMEOUT = 30;
    private static final Topic NDEATH_TOPIC =
            new Topic(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX, GROUP_ID, EDGE_NODE_ID, MessageType.NDEATH);

    private static final List<MqttServerDefinition> mqttServerDefinitions = new ArrayList<>();

    private static DataSimulator dataSimulator = new RandomDataSimulator(
            10, DEVICE_DESCRIPTORS.stream().collect(Collectors.toMap(Function.identity(), __ -> 50)));

    private static final BdSeqManager bdSeqManager = new AtomicBdSeqManager();
    private static final ExecutorService handlerExecutorService = Executors.newSingleThreadExecutor();

    private TahuEdgeClient tahuEdgeClient;

    @BeforeAll
    public static void beforeAll() throws Exception {
        mqttServerDefinitions.add(new MqttServerDefinition(
                MQTT_SERVER_NAME_1,
                new MqttClientId(MQTT_CLIENT_ID_1, false),
                MqttServerUrl.getMqttServerUrlSafe(SparkplugTCKService.getMqttHostAddress()),
                USERNAME_1,
                PASSWORD_1,
                KEEP_ALIVE_TIMEOUT,
                NDEATH_TOPIC));
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        tahuEdgeClient = new TahuEdgeClient.ClientBuilder()
                .edgeNodeDescriptor(EDGE_NODE_DESCRIPTOR)
                .deviceIds(DEVICE_IDS)
                .primaryHostId(PRIMARY_HOST_ID)
                .useAliases(USE_ALIASES)
                .rebirthDebounceDelay(REBIRTH_DEBOUNCE_DELAY)
                .serverDefinitions(mqttServerDefinitions)
                .bdSeqManager(bdSeqManager)
                .clientExecutorService(handlerExecutorService)
                .build();

        tahuEdgeClient.addDeviceMetricDataPayloadMap(
                EDGE_NODE_DESCRIPTOR, dataSimulator.getNodeBirthPayload(EDGE_NODE_DESCRIPTOR));

        for (DeviceDescriptor deviceDescriptor : DEVICE_DESCRIPTORS) {
            SparkplugBPayloadMap deviceMetricPayloadMap = new SparkplugBPayloadMap();

            deviceMetricPayloadMap.setMetrics(
                    dataSimulator.getDeviceBirthPayload(deviceDescriptor).getMetrics());

            tahuEdgeClient.addDeviceMetricDataPayloadMap(deviceDescriptor, deviceMetricPayloadMap);
        }
    }

    @AfterEach
    public void afterEach() throws Exception {
        spTckService.resetTckTest();

        tahuEdgeClient.shutdown();
    }

    static Stream<Arguments> handlerTestArgsProvider() {
        return Stream.of(
                Arguments.of("SessionEstablishmentTest", false, 0),
                Arguments.of("SessionTerminationTest", false, 3),
                Arguments.of("SendDataTest", false, 15),
                Arguments.of("SendComplexDataTest", false, 15),
                Arguments.of("ReceiveCommandTest", false, 15),
                Arguments.of("PrimaryHostTest", true, 15));
    }

    @ParameterizedTest
    @MethodSource("handlerTestArgsProvider")
    public void handlerTest(String testName, boolean startHandlerBeforeTCKInitiate, int maxSentDataCount)
            throws Exception {
        LOG.info("handlerTest: Starting {}", testName);

        if (startHandlerBeforeTCKInitiate) {
            tahuEdgeClient.startup();
        }

        initiateTckTest(testName);

        if (!startHandlerBeforeTCKInitiate) {
            tahuEdgeClient.startup();
        }

        Instant timeout = Instant.now().plusSeconds(15L);

        int sentDataCount = 0;
        while (sentDataCount < maxSentDataCount && Instant.now().isBefore(timeout)) {

            sendData();

            sentDataCount += 1;

            if (spTckService.spTckResultMockNotify.matches(1L, TimeUnit.SECONDS)) {
                break;
            }
        }

        if ("SessionTerminationTest".equals(testName)) {
            tahuEdgeClient.shutdown();
        }

        spTckService.spTckResultMockEndpoint.assertIsSatisfied();
    }

    private String createEdgeTestParams(String testName) {
        String deviceIdsParams = DEVICE_IDS.stream().collect(Collectors.joining(" "));
        return List.of("edge", testName, PRIMARY_HOST_ID, GROUP_ID, EDGE_NODE_ID, deviceIdsParams).stream()
                .collect(Collectors.joining(" "));
    }

    private void initiateTckTest(String testName) throws Exception {
        String testParams = createEdgeTestParams(testName);
        spTckService.initiateTckTest(testParams);
    }

    private void sendData() throws Exception {
        publishSimPayload(EDGE_NODE_DESCRIPTOR);

        for (DeviceDescriptor deviceDescriptor : DEVICE_DESCRIPTORS) {
            publishSimPayload(deviceDescriptor);
        }
    }

    private void publishSimPayload(SparkplugDescriptor sd) {
        SparkplugBPayload simPayload;
        if (sd.isDeviceDescriptor()) {
            simPayload = dataSimulator.getDeviceDataPayload((DeviceDescriptor) sd);
        } else {
            simPayload = dataSimulator.getNodeDataPayload((EdgeNodeDescriptor) sd);
        }

        tahuEdgeClient.publishData(sd, simPayload);
    }
}
