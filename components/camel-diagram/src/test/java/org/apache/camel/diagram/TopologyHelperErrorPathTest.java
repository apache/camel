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

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25429: the diagrams draw the happy path; the error paths (onException, dead letter channel) are only there when
 * asked for.
 */
class TopologyHelperErrorPathTest {

    @Test
    void theErrorPathsAreLeftOutUnlessAskedFor() {
        JsonObject root = new JsonObject();
        JsonArray edges = new JsonArray();
        edges.add(edge("checkout", "payment-provider", "call"));
        edges.add(edge("checkout", "parked", "deadLetter"));
        edges.add(edge("parked", "parked", "onException"));
        // an older integration does not say the kind
        edges.add(edge("legacy", "parked", null));
        root.put("edges", edges);

        List<TopologyEdgeInfo> calls = TopologyHelper.parseEdges(root);
        assertThat(calls).extracting(e -> e.fromRouteId + "->" + e.toRouteId)
                .containsExactly("checkout->payment-provider", "legacy->parked");

        List<TopologyEdgeInfo> all = TopologyHelper.parseEdges(root, true);
        assertThat(all).hasSize(4);
        assertThat(all).filteredOn(TopologyEdgeInfo::isErrorPath).extracting(e -> e.kind)
                .containsExactly("deadLetter", "onException");
    }

    private static JsonObject edge(String from, String to, String kind) {
        JsonObject e = new JsonObject();
        e.put("fromRouteId", from);
        e.put("toRouteId", to);
        e.put("endpoint", "direct:" + to);
        e.put("connectionType", "internal");
        if (kind != null) {
            e.put("kind", kind);
        }
        return e;
    }
}
