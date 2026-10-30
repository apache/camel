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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The parser's handling of documents an agent or something in its place may send. */
class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @Test
    void aDocumentTypeDeclarationIsRejectedSoNoExternalEntityIsResolved() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE MTConnectStreams [<!ENTITY secret SYSTEM \"file:///etc/hosts\">]>"
                     + "<MTConnectStreams><Header instanceId=\"1\" nextSequence=\"2\"/><Streams>&secret;</Streams>"
                     + "</MTConnectStreams>";

        assertThatThrownBy(() -> parser.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(XMLStreamException.class);
    }

    @Test
    void anErrorDocumentIsRecognisedByItsRootElement() throws Exception {
        String xml
                = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<MTConnectError xmlns=\"urn:mtconnect.org:MTConnectError:1.3\">\n"
                  + "  <Header instanceId=\"1791952752\" bufferSize=\"8192\"/>\n  <Errors>\n"
                  + "    <Error errorCode=\"OUT_OF_RANGE\">'from' must be greater than or equal to 3698885.</Error>\n"
                  + "  </Errors>\n</MTConnectError>\n";

        AgentDocument document = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(document).isInstanceOfSatisfying(AgentDocument.Error.class, error -> {
            assertThat(error.hasCode("OUT_OF_RANGE")).isTrue();
            assertThat(error.header().instanceId()).isEqualTo(1791952752L);
        });
    }

    @Test
    void timeSeriesArriveAsDecimalListsAndTablesAsMapsOfRows() throws Exception {
        String xml = "<MTConnectStreams xmlns=\"urn:mtconnect.org:MTConnectStreams:2.5\"><Header instanceId=\"1\""
                     + " nextSequence=\"9\" lastSequence=\"8\"/><Streams><DeviceStream name=\"d\" uuid=\"u\">"
                     + "<ComponentStream component=\"Linear\" componentId=\"x\"><Samples>"
                     + "<PositionTimeSeries dataItemId=\"xts\" sampleCount=\"3\" sequence=\"7\""
                     + " timestamp=\"2026-10-27T15:02:22Z\">1.5 2 -3</PositionTimeSeries></Samples><Events>"
                     + "<WorkOffsetTable count=\"1\" dataItemId=\"wot\" sequence=\"8\" timestamp=\"2026-10-27T15:02:22Z\">"
                     + "<Entry key=\"G54\"><Cell key=\"X\">10</Cell><Cell key=\"Y\">20</Cell></Entry></WorkOffsetTable>"
                     + "</Events></ComponentStream></DeviceStream></Streams></MTConnectStreams>";

        AgentDocument.Streams streams = (AgentDocument.Streams) parser.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(streams.observations()).extracting(Observation::value).containsExactly(
                List.of(new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("-3")),
                Map.of("G54", Map.of("X", "10", "Y", "20")));
    }

    @Test
    void aWellFormedPageThatIsNotAnMTConnectDocumentIsRejected() {
        String html = "<html><body><h1>Bad Gateway</h1></body></html>";

        assertThatThrownBy(() -> parser.parse(html.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(XMLStreamException.class).hasMessageContaining("root element html");
    }
}
