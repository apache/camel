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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Asset messages of the consumer with assets=true, against the stub's asset store. */
class MTConnectAssetsTest extends MTConnectTestSupport {

    private static final String TOOL = "11072000.519";
    private static final String PROGRAM = "VTC300C-ATC-B-C0010-OP1.EIA";

    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("collectingHandler", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception.getMessage());
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(message + ": " + exception.getMessage());
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(message);
            }
        });
    }

    @Test
    void assetsAreOffByDefaultAndAssetEventsStillArriveAsObservations() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "");

        agent.changeAsset(TOOL);

        awaitAgent().untilAsserted(() -> assertThat(received.of("d1_asset_chg")).last()
                .satisfies(m -> assertThat(m.body()).isEqualTo(TOOL))
                .satisfies(m -> assertThat(m.headers()).containsEntry(MTConnectConstants.ASSET_TYPE, "CuttingTool")));
        assertThat(assetMessages(received)).isEmpty();
        assertThat(agent.requests()).noneMatch(request -> request.startsWith("/asset"));
    }

    @Test
    void everyAssetArrivesOnceAfterTheCurrentValues() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));
        assertThat(assetMessages(received)).extracting(m -> m.text(MTConnectConstants.ASSET_TYPE))
                .containsExactlyInAnyOrder("CuttingTool", "CuttingTool", "File", "File", "Fixture");
        assertThat(assetMessages(received)).allSatisfy(m -> assertThat(m.headers())
                .containsEntry(MTConnectConstants.ASSET_REMOVED, false)
                .containsEntry(MTConnectConstants.DEVICE_UUID, "11111111-2222-3333-4444-555555555555")
                .containsEntry(MTConnectConstants.DEVICE_NAME, "MACHINE-01")
                .containsEntry(MTConnectConstants.AGENT, agent.url())
                .containsKeys(MTConnectConstants.ASSET_ID, MTConnectConstants.TIMESTAMP, MTConnectConstants.INSTANCE_ID)
                .doesNotContainKeys(MTConnectConstants.QUALITY, MTConnectConstants.SEQUENCE));
        List<ReceivedMessages.Received> all = received.all();
        assertThat(all.indexOf(assetMessages(received).get(0)))
                .isGreaterThan(all.indexOf(received.matching(ReceivedMessages.Received::current).get(10)));
        assertThat(agent.requests()).contains("/assets?device=MACHINE-01&count=8096&removed=true");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "V1_3, MACHINE-01, agents/v13-assets.xml, 5", "V2_5, , agents/v28-assets.xml, 3" })
    void anAssetBodyIsTheAgentElementAsAStandaloneDocument(
            StubDialect dialect, String device, String capture, int count)
            throws Exception {
        StubAgent agent = agent(dialect);
        ReceivedMessages received = streaming(agent, device, "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(count));
        Document captured = parse(resource(capture));
        Element capturedRoot = captured.getDocumentElement();

        for (ReceivedMessages.Received message : assetMessages(received)) {
            Element body = parse(((String) message.body()).getBytes(StandardCharsets.UTF_8)).getDocumentElement();
            Element element = assetElement(captured, message.text(MTConnectConstants.ASSET_ID));
            assertThat(body.getLocalName()).isEqualTo(element.getLocalName());
            assertThat(body.getNamespaceURI()).isEqualTo(element.getNamespaceURI());
            assertThat(attributes(body, false)).isEqualTo(attributes(element, false));
            // every namespace declared on the agent's document is in scope on the standalone asset
            assertThat(attributes(body, true)).containsAllEntriesOf(attributes(capturedRoot, true));
            assertThat(body.getTextContent()).isEqualTo(element.getTextContent());
            assertThat(message.headers()).containsEntry(MTConnectConstants.ASSET_TYPE, element.getLocalName())
                    .containsEntry(MTConnectConstants.TIMESTAMP, Instant.parse(element.getAttribute("timestamp")));
        }
    }

    @Test
    void aChangedAssetArrivesAfterItsAssetChangedObservation() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));

        agent.changeAsset(TOOL);

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received, TOOL)).hasSize(2));
        ReceivedMessages.Received changed = assetMessages(received, TOOL).get(1);
        ReceivedMessages.Received event = received.of("d1_asset_chg").get(1);
        assertThat(received.all().indexOf(changed)).isGreaterThan(received.all().indexOf(event));
        assertThat((Instant) changed.header(MTConnectConstants.TIMESTAMP))
                .isAfter((Instant) assetMessages(received, TOOL).get(0).header(MTConnectConstants.TIMESTAMP));
        assertThat(changed.headers()).containsEntry(MTConnectConstants.ASSET_REMOVED, false);
        assertThat(agent.requests()).contains("/asset/" + TOOL);
    }

    @Test
    void aRemovedAssetArrivesWithTheRemovedFlag() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));

        agent.removeAsset(TOOL);
        agent.dropAsset(PROGRAM);

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(7));
        assertThat(received.of("d1_asset_rem")).extracting(ReceivedMessages.Received::body).contains(TOOL, PROGRAM);
        assertThat(assetMessages(received, TOOL)).last()
                .satisfies(m -> assertThat(m.headers()).containsEntry(MTConnectConstants.ASSET_REMOVED, true))
                .satisfies(m -> assertThat((String) m.body()).contains("removed=\"true\""));
        // the agent no longer holds the program: no body, the removed flag and the event's type and time
        assertThat(assetMessages(received, PROGRAM)).last()
                .satisfies(m -> assertThat(m.body()).isNull())
                .satisfies(m -> assertThat(m.headers()).containsEntry(MTConnectConstants.ASSET_REMOVED, true)
                        .containsEntry(MTConnectConstants.ASSET_TYPE, "File")
                        .containsEntry(MTConnectConstants.DOCUMENT, MTConnectConstants.DOCUMENT_ASSET)
                        .containsKey(MTConnectConstants.TIMESTAMP));
    }

    @Test
    void aFailedAssetReadIsAnOutageAndTheAssetFollowsOnRecovery() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));
        int items = received.matching(ReceivedMessages.Received::current).size();

        agent.answer(StubAgent.Request.ASSET, StubAgent.Answer.html(502));
        agent.changeAsset(TOOL);

        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> m.bad() && m.sequence() == null)).hasSize(items)
                .allSatisfy(m -> assertThat(m.headers()).containsEntry(MTConnectConstants.ERROR, "HTTP 502")));
        assertThat(assetMessages(received)).noneMatch(m -> m.bad());
        agent.clearAnswers();

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received, TOOL)).hasSize(2));
        assertThat(received.of("d1_asset_chg")).filteredOn(ReceivedMessages.Received::sample).hasSize(1);
    }

    @Test
    void theTypeFilterLimitsTheReadsAndTheMessages() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true&assetType=CuttingTool");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(2));

        agent.changeAsset(PROGRAM);
        agent.changeAsset(TOOL);

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(3));
        assertThat(assetMessages(received)).extracting(m -> m.text(MTConnectConstants.ASSET_TYPE))
                .containsOnly("CuttingTool");
        assertThat(received.of("d1_asset_chg")).extracting(ReceivedMessages.Received::body).contains(PROGRAM, TOOL);
        assertThat(agent.requests()).contains("/assets?device=MACHINE-01&type=CuttingTool&count=8096&removed=true")
                .doesNotContain("/asset/" + PROGRAM);
    }

    @Test
    void anAssetVersionIsSentOnce() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));

        agent.announceAsset(TOOL);
        long marker = agent.set("xl", "1");

        awaitAgent().untilAsserted(() -> assertThat(agent.requests()).contains("/asset/" + TOOL));
        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> Long.valueOf(marker).equals(m.sequence())))
                .isNotEmpty());
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5))
                .until(() -> assetMessages(received, TOOL).size() == 1);
    }

    @Test
    void anOverrunReadsTheAssetsAgainAndDeliversTheChangeItLost() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));

        agent.changeAssetSilently(TOOL);
        agent.overrun("xl");

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received, TOOL)).hasSize(2));
        assertThat(agent.requests()).filteredOn(request -> request.startsWith("/assets")).hasSize(2);
        // the other assets did not change and are not sent again
        assertThat(assetMessages(received)).hasSize(6);
        assertThat(received.matching(m -> MTConnectConstants.RESYNC_OUT_OF_RANGE
                .equals(m.header(MTConnectConstants.RESYNC)))).isNotEmpty();
    }

    @Test
    void anAssetRemovedDuringAnOverrunArrivesAsRemovedAfterRecovery() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));

        agent.removeAssetSilently(TOOL);
        agent.overrun("xl");

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received, TOOL)).hasSize(2).last()
                .satisfies(m -> assertThat(m.headers()).containsEntry(MTConnectConstants.ASSET_REMOVED, true))
                .satisfies(m -> assertThat((String) m.body()).contains("removed=\"true\"")));
        // the removal was never reported as an observation; the full read after the resynchronisation brings it
        assertThat(received.of("d1_asset_rem")).noneMatch(ReceivedMessages.Received::sample);
        assertThat(assetMessages(received)).hasSize(6);
    }

    @Test
    void anAgentErrorToTheAssetsRequestIsOneOutageWithoutReadingCurrentAgain() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        agent.answer(StubAgent.Request.ASSETS, new StubAgent.Answer(400, "INVALID_REQUEST"));
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?device=MACHINE-01&" + FAST
                                            + "&assets=true&exceptionHandler=#collectingHandler");
        long items = agent.model().items().stream().filter(item -> StubDeviceModel.matches(item, "MACHINE-01")).count();
        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> m.bad() && m.sequence() == null))
                .hasSize((int) items));

        // retries of the assets request report nothing more and do not deliver the current values again
        await().during(Duration.ofMillis(4 * RECONNECT_DELAY)).atMost(Duration.ofSeconds(5))
                .until(() -> handled.size() == 1
                        && received.matching(ReceivedMessages.Received::current).size() == items
                        && received.matching(m -> m.bad() && m.sequence() == null).size() == items);
        assertThat(handled.get(0)).contains("INVALID_REQUEST");
        assertThat(agent.requests()).filteredOn(request -> request.endsWith("/probe")).hasSize(1);
        assertThat(agent.requests()).filteredOn(request -> request.startsWith("/assets")).hasSizeGreaterThan(2);
        agent.clearAnswers();

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));
    }

    @Test
    void anAgentRestartReadsTheAssetsAgain() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = streaming(agent, "MACHINE-01", "assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(5));

        agent.changeAssetSilently(PROGRAM);
        agent.restart();

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received, PROGRAM)).hasSize(2));
        assertThat(assetMessages(received, PROGRAM).get(1).header(MTConnectConstants.INSTANCE_ID))
                .isEqualTo(agent.instanceId());
        assertThat(assetMessages(received)).hasSize(6);
        assertThat(agent.requests()).filteredOn(request -> request.startsWith("/assets")).hasSize(2);
    }

    @Test
    void aPollingConsumerSendsAssetMessagesToo() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?streaming=false&delay=50&assets=true");
        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received)).hasSize(3));

        agent.addAsset("<CuttingTool assetId=\"T9.1\" deviceUuid=\"trial-001\" timestamp=\"2026-10-28T08:00:00Z\""
                       + " serialNumber=\"1\" toolId=\"T9\"><CuttingToolLifeCycle><ToolLife type=\"MINUTES\">5"
                       + "</ToolLife></CuttingToolLifeCycle></CuttingTool>");

        awaitAgent().untilAsserted(() -> assertThat(assetMessages(received, "T9.1")).singleElement()
                .satisfies(m -> assertThat((String) m.body()).contains("<ToolLife type=\"MINUTES\">5</ToolLife>")));
        assertThat(received.of("agent_31e6d9c6_asset_chg")).extracting(ReceivedMessages.Received::body).contains("T9.1");
    }

    private static List<ReceivedMessages.Received> assetMessages(ReceivedMessages received) {
        return received.matching(m -> MTConnectConstants.DOCUMENT_ASSET.equals(m.header(MTConnectConstants.DOCUMENT)));
    }

    private static List<ReceivedMessages.Received> assetMessages(ReceivedMessages received, String id) {
        return assetMessages(received).stream().filter(m -> id.equals(m.text(MTConnectConstants.ASSET_ID))).toList();
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = MTConnectAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            return in.readAllBytes();
        }
    }

    private static Document parse(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    private static Element assetElement(Document document, String assetId) {
        Element assets = (Element) document.getDocumentElement().getElementsByTagNameNS("*", "Assets").item(0);
        for (Node node = assets.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && assetId.equals(element.getAttribute("assetId"))) {
                return element;
            }
        }
        throw new AssertionError("No asset " + assetId);
    }

    /** The attributes of an element by qualified name: the namespace declarations, or the others. */
    private static Map<String, String> attributes(Element element, boolean declarations) {
        Map<String, String> attributes = new HashMap<>();
        NamedNodeMap map = element.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            Attr attribute = (Attr) map.item(i);
            if (attribute.getName().startsWith("xmlns") == declarations) {
                attributes.put(attribute.getName(), attribute.getValue());
            }
        }
        return attributes;
    }
}
