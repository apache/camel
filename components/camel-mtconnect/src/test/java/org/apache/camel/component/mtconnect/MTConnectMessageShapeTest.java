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
package org.apache.camel.component.mtconnect;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** What a route author sees in each message: the body and the probe's metadata as headers. */
class MTConnectMessageShapeTest extends MTConnectTestSupport {

    @ParameterizedTest
    @CsvSource({ "V2_5, Trial", "V1_3, MACHINE-01", "V1_5, MACHINE-02" })
    void everyDataItemArrivesWithTheProbeMetadataOfItsId(StubDialect dialect, String device) throws Exception {
        StubAgent agent = agent(dialect);
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?device=" + device + "&" + FAST);
        List<StubDeviceModel.Item> items = agent.model().items().stream()
                .filter(item -> StubDeviceModel.matches(item, device)).toList();

        awaitAgent().untilAsserted(() -> assertThat(received.size()).isGreaterThanOrEqualTo(items.size()));
        for (StubDeviceModel.Item item : items) {
            ReceivedMessages.Received message = received.of(item.id()).get(0);
            assertThat(message.headers()).as(item.id())
                    .containsEntry(MTConnectConstants.AGENT, agent.url())
                    .containsEntry(MTConnectConstants.DEVICE_NAME, item.deviceName())
                    .containsEntry(MTConnectConstants.DEVICE_UUID, item.deviceUuid())
                    .containsEntry(MTConnectConstants.COMPONENT_TYPE, item.componentType())
                    .containsEntry(MTConnectConstants.COMPONENT_ID, item.componentId())
                    .containsEntry(MTConnectConstants.CATEGORY, item.category())
                    .containsEntry(MTConnectConstants.TYPE, item.type())
                    .containsEntry(MTConnectConstants.INSTANCE_ID, agent.instanceId())
                    .containsEntry(MTConnectConstants.DOCUMENT, MTConnectConstants.DOCUMENT_CURRENT)
                    .containsKey(MTConnectConstants.SEQUENCE)
                    .containsKey(MTConnectConstants.TIMESTAMP);
            assertThat(message.text(MTConnectConstants.COMPONENT_NAME)).as(item.id()).isEqualTo(item.componentName());
            assertThat(message.text(MTConnectConstants.DATA_ITEM_NAME)).as(item.id()).isEqualTo(item.name());
            assertThat(message.text(MTConnectConstants.SUB_TYPE)).as(item.id()).isEqualTo(item.subType());
            assertThat(message.text(MTConnectConstants.UNITS)).as(item.id()).isEqualTo(item.units());
            assertThat(message.text(MTConnectConstants.NATIVE_UNITS)).as(item.id()).isEqualTo(item.nativeUnits());
            // a freshly started agent reports every item UNAVAILABLE
            assertThat(message.body()).as(item.id()).isNull();
            assertThat(message.bad()).as(item.id()).isTrue();
            assertThat(message.text(MTConnectConstants.REASON)).isEqualTo(MTConnectConstants.REASON_UNAVAILABLE);
        }
    }

    @Test
    void numericSamplesArriveAsDecimalsAndEventsAsTheAgentSentThem() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        agent.set("x_load", "11.5");
        agent.set("exec", "ACTIVE");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(2));
        ReceivedMessages.Received load = received.of("x_load").get(1);
        assertThat(load.body()).isEqualTo(new BigDecimal("11.5"));
        assertThat(load.good()).isTrue();
        assertThat(load.headers()).doesNotContainKey(MTConnectConstants.REASON)
                .containsEntry(MTConnectConstants.UNITS, "PERCENT")
                .containsEntry(MTConnectConstants.REPRESENTATION, "VALUE");
        assertThat(received.of("exec").get(1).body()).isEqualTo("ACTIVE");
    }

    @Test
    void dataSetsArriveAsMaps() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Agent", "");
        agent.setEntries("agent_31e6d9c6_asset_count", Map.of("CuttingTool", "15"));

        awaitAgent().untilAsserted(() -> assertThat(received.of("agent_31e6d9c6_asset_count")).hasSize(2));
        ReceivedMessages.Received assets = received.of("agent_31e6d9c6_asset_count").get(1);
        assertThat(assets.body()).isEqualTo(Map.of("CuttingTool", "15"));
        assertThat(assets.headers()).containsEntry(MTConnectConstants.REPRESENTATION, "DATA_SET")
                .containsEntry(MTConnectConstants.COMPONENT_TYPE, "Agent");
    }

    @Test
    void conditionsCarryTheirLevelNativeCodeQualifierAndText() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        agent.condition("sys_cond", "Fault", "E42", null, "HIGH", "Spindle overheat");
        agent.condition("sys_cond", "Normal", null, null, null, null);

        awaitAgent().untilAsserted(() -> assertThat(received.of("sys_cond")).hasSize(3));
        List<ReceivedMessages.Received> conditions = received.of("sys_cond");
        assertThat(conditions.get(0).headers()).containsEntry(MTConnectConstants.LEVEL, "Unavailable")
                .containsEntry(MTConnectConstants.QUALITY, MTConnectConstants.QUALITY_BAD)
                .containsEntry(MTConnectConstants.REASON, MTConnectConstants.REASON_UNAVAILABLE);
        assertThat(conditions.get(1).body()).isEqualTo("Spindle overheat");
        assertThat(conditions.get(1).headers()).containsEntry(MTConnectConstants.LEVEL, "Fault")
                .containsEntry(MTConnectConstants.NATIVE_CODE, "E42")
                .containsEntry(MTConnectConstants.QUALIFIER, "HIGH")
                .containsEntry(MTConnectConstants.TYPE, "SYSTEM")
                .containsEntry(MTConnectConstants.QUALITY, MTConnectConstants.QUALITY_GOOD)
                .doesNotContainKey(MTConnectConstants.NATIVE_SEVERITY);
        assertThat(conditions.get(2).body()).isNull();
        assertThat(conditions.get(2).headers()).containsEntry(MTConnectConstants.LEVEL, "Normal");
    }

    @Test
    void optionalObservationAttributesArriveAsHeaders() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        agent.set("temp", "21.5", Map.of("statistic", "AVERAGE", "duration", "60.5", "resetTriggered", "MANUAL",
                "compositionId", "motor1", "sampleRate", "100"));
        agent.set("exec", "ACTIVE", Map.of("assetType", "CuttingTool"));

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(2));
        assertThat(received.of("temp").get(1).headers())
                .containsEntry(MTConnectConstants.STATISTIC, "AVERAGE")
                .containsEntry(MTConnectConstants.DURATION, new BigDecimal("60.5"))
                .containsEntry(MTConnectConstants.RESET_TRIGGERED, "MANUAL")
                .containsEntry(MTConnectConstants.COMPOSITION_ID, "motor1")
                .containsEntry(MTConnectConstants.SAMPLE_RATE, new BigDecimal("100"));
        assertThat(received.of("exec").get(1).headers()).containsEntry(MTConnectConstants.ASSET_TYPE, "CuttingTool")
                .doesNotContainKeys(MTConnectConstants.STATISTIC, MTConnectConstants.DURATION);
    }

    @Test
    void itemsWithTheSameNameStayDistinctByDataItemId() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        agent.set("x_load", "11.5");
        agent.set("y_load", "88.25");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(2));
        assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::dataItemId, m -> m.text(MTConnectConstants.DATA_ITEM_NAME),
                        m -> m.text(MTConnectConstants.COMPONENT_NAME), ReceivedMessages.Received::body)
                .containsExactly(
                        tuple("x_load", "load", "X", new BigDecimal("11.5")),
                        tuple("y_load", "load", "Y", new BigDecimal("88.25")));
    }

    @Test
    void vendorNamespaceObservationsOfAOneThreeAgentParse() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "");
        agent.set("tid2", "2005212");
        agent.set("xl", "12.5");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(2));
        ReceivedMessages.Received toolGroup = received.of("tid2").get(1);
        assertThat(toolGroup.body()).isEqualTo("2005212");
        assertThat(toolGroup.headers()).containsEntry(MTConnectConstants.TYPE, "x:TOOL_GROUP")
                .containsEntry(MTConnectConstants.CATEGORY, "EVENT");
        assertThat(received.of("xl").get(1).body()).isEqualTo(new BigDecimal("12.5"));
    }
}
