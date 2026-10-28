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
package org.apache.camel.diagram;

import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDiagramHelperTest {

    @Test
    void parseRoutesKeepsTheBodyTypesOfEachStep() {
        JsonObject structure = (JsonObject) Jsoner.deserialize("""
                {"routes": [{"routeId": "orders", "code": [
                  {"type": "route", "id": "orders", "level": 0, "code": "route"},
                  {"type": "from", "id": "from1", "level": 0, "code": "from[platform-http:/orders]"},
                  {"type": "unmarshal", "id": "unmarshal1", "level": 1, "code": "unmarshal[json]",
                   "bodyTypes": ["org.apache.camel.converter.stream.ByteArrayInputStreamCache"], "bodyKinds": ["payload"]},
                  {"type": "to", "id": "to1", "level": 1, "code": "to[file:outbox]",
                   "bodyTypes": ["byte[]", "java.util.LinkedHashMap"], "bodyKinds": ["payload", "data"]}]}]}
                """, (JsonObject) null);

        RouteInfo route = RouteDiagramHelper.parseRoutes(structure).get(0);

        NodeInfo from = route.nodes.get(1);
        NodeInfo to = route.nodes.get(3);
        // the from has none: the body it gave is what the first step was reached with
        assertThat(from.bodyTypes).isEmpty();
        assertThat(from.bodyKinds).isEmpty();
        assertThat(route.nodes.get(2).bodyTypes)
                .containsExactly("org.apache.camel.converter.stream.ByteArrayInputStreamCache");
        assertThat(to.bodyTypes).containsExactly("byte[]", "java.util.LinkedHashMap");
        assertThat(to.bodyKinds).containsExactly("payload", "data");
    }

    @Test
    void wrapTextShortReturnedAsIs() {
        assertThat(RouteDiagramHelper.wrapText("short", 20)).containsExactly("short");
    }

    @Test
    void wrapTextLongWrapsAtBreakCharacters() {
        List<String> lines = RouteDiagramHelper.wrapText("kafka:my-topic?brokers=localhost:9092", 15);
        assertThat(lines).hasSizeGreaterThan(1);
        assertThat(String.join("", lines)).contains("kafka").contains("localhost");
    }

    @Test
    void wrapTextRemainingThatFitsOnLastLineIsAppendedWithoutEllipsis() {
        // "aaa bbb ccc dd" with maxWidth=5:
        //   round 1 → "aaa", round 2 → "bbb", round 3 → "ccc", remaining = "dd"
        //   lastLine("ccc").len=3 + remaining("dd").len=2 = 5 <= maxWidth → append, no "..."
        List<String> lines = RouteDiagramHelper.wrapText("aaa bbb ccc dd", 5);
        assertThat(lines).containsExactly("aaa", "bbb", "cccdd");
    }

    @Test
    void wrapTextRemainingThatDoesNotFitOnLastLineIsTruncatedWithEllipsis() {
        // Same structure but remaining = "ddddd" (len 5): 3+5=8 > 5 → truncate with "..."
        List<String> lines = RouteDiagramHelper.wrapText("aaa bbb ccc ddddd", 5);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(2)).endsWith("...");
    }
}
