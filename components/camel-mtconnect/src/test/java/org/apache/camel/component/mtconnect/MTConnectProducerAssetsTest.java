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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The producer's assets requests against the stub's asset store, seeded from captured asset documents. */
class MTConnectProducerAssetsTest extends MTConnectTestSupport {

    @Test
    void assetsOfATypeArriveAsTheAgentAssetsDocument() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=MACHINE-01&operation=assets&assetType=CuttingTool",
                Map.of());

        Document document = parse(reply);
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("MTConnectAssets");
        assertThat(assets(document)).extracting(Element::getLocalName).containsOnly("CuttingTool").hasSize(2);
        assertThat(reply.getMessage().getHeader(MTConnectConstants.INSTANCE_ID)).isEqualTo(agent.instanceId());
        assertThat(agent.requests()).containsExactly("/assets?device=MACHINE-01&type=CuttingTool&count=1000");
    }

    @Test
    void everyAssetOfTheDeviceArrivesWithTheVendorNamespaceOfTheDocument() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);

        Exchange reply = send("mtconnect:" + agent.url() + "?device=MACHINE-01&operation=assets", Map.of());

        Document document = parse(reply);
        assertThat(assets(document)).extracting(Element::getLocalName)
                .containsExactlyInAnyOrder("CuttingTool", "CuttingTool", "File", "File", "Fixture");
        assertThat(document.getDocumentElement().getAttribute("xmlns:x")).isEqualTo("urn:example.com:VendorAssets:1.0");
        assertThat(reply.getMessage().getBody(String.class)).contains("<x:ClampPressure");
    }

    @Test
    void assetIdsFromTheHeaderReadThoseAssetsOnly() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        String ids = "11072000.519;VTC300C-ATC-B-C0010-OP1.EIA";

        Exchange reply = send("mtconnect:" + agent.url() + "?device=MACHINE-01&operation=assets&assetType=Fixture",
                Map.of(MTConnectConstants.ASSET_ID, ids));

        assertThat(assets(parse(reply))).extracting(asset -> asset.getAttribute("assetId"))
                .containsExactlyInAnyOrder("11072000.519", "VTC300C-ATC-B-C0010-OP1.EIA");
        assertThat(agent.requests()).containsExactly("/asset/" + ids);
    }

    @Test
    void theTypeHeaderAndTheCountBoundTheReply() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);

        Exchange files = send("mtconnect:" + agent.url() + "?operation=assets&assetType=CuttingTool",
                Map.of(MTConnectConstants.ASSET_TYPE, "File"));
        Exchange one = send("mtconnect:" + agent.url() + "?operation=assets&count=1", Map.of());
        Exchange two = send("mtconnect:" + agent.url() + "?operation=assets&count=5",
                Map.of(MTConnectConstants.COUNT, 2));

        assertThat(assets(parse(files))).isEmpty();
        assertThat(assets(parse(one))).hasSize(1);
        assertThat(assets(parse(two))).hasSize(2);
    }

    @Test
    void removedAssetsArriveOnlyWhenAskedFor() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.removeAsset("123456.2202");

        Exchange held = send("mtconnect:" + agent.url() + "?device=Trial&operation=assets", Map.of());
        Exchange all = send("mtconnect:" + agent.url() + "?device=Trial&operation=assets&removed=true", Map.of());

        assertThat(assets(parse(held))).extracting(asset -> asset.getAttribute("assetId"))
                .containsExactlyInAnyOrder("123456.2208", "123456.2201");
        assertThat(assets(parse(all))).filteredOn(asset -> "true".equals(asset.getAttribute("removed")))
                .extracting(asset -> asset.getAttribute("assetId")).containsExactly("123456.2202");
        assertThat(agent.requests()).last().isEqualTo("/assets?device=Trial&count=1000&removed=true");
    }

    @ParameterizedTest(name = "{0} answers ASSET_NOT_FOUND with {1}")
    @CsvSource({ "V2_5, 404", "V1_3, 200" })
    void anUnknownAssetIdFailsTheExchangeWithTheAgentError(StubDialect dialect, int status) throws Exception {
        StubAgent agent = agent(dialect);

        Exchange reply = send("mtconnect:" + agent.url() + "?operation=assets",
                Map.of(MTConnectConstants.ASSET_ID, "T-404"));

        assertThat(reply.getException()).isInstanceOfSatisfying(MTConnectErrorException.class, e -> {
            assertThat(e.getErrorCodes()).containsExactly("ASSET_NOT_FOUND");
            assertThat(e.getErrorTexts()).containsExactly("Could not find asset: T-404");
        });
    }

    private Exchange send(String uri, Map<String, Object> headers) {
        return template.send(uri, exchange -> exchange.getMessage().setHeaders(new HashMap<>(headers)));
    }

    private static Document parse(Exchange reply) throws Exception {
        assertThat(reply.getException()).isNull();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        byte[] body = reply.getMessage().getBody(String.class).getBytes(StandardCharsets.UTF_8);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
    }

    private static List<Element> assets(Document document) {
        Element container = (Element) document.getDocumentElement().getElementsByTagNameNS("*", "Assets").item(0);
        List<Element> assets = new ArrayList<>();
        for (Node node = container.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                assets.add(element);
            }
        }
        return assets;
    }
}
