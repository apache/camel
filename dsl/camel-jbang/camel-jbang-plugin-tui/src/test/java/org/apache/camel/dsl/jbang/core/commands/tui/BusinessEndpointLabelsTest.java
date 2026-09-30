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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CAMEL-25161: the endpoints of a route diagram in plain words for the business view. */
class BusinessEndpointLabelsTest {

    @Test
    void componentTitleAndPath() {
        DefaultCamelCatalog catalog = new DefaultCamelCatalog();
        assertThat(BusinessEndpointLabels.label("file:src/main/data", catalog)).isEqualTo("File: src/main/data");
        assertThat(BusinessEndpointLabels.label("amqp:queue:order.queue", catalog)).isEqualTo("AMQP: queue:order.queue");
        assertThat(BusinessEndpointLabels.label("kafka://orders", catalog)).isEqualTo("Kafka: orders");
        assertThat(BusinessEndpointLabels.label("acme:thing", catalog)).isEqualTo("Acme: thing");
        assertThat(BusinessEndpointLabels.label(null, catalog)).isNull();
    }

    @Test
    void systemOfAnEdge() {
        // CAMEL-25161: the system a message leaves to or comes in from, beside the box
        assertThat(BusinessEndpointLabels.systemName(node("to", "to[amqp:queue:widget.queue]", null))).isEqualTo("AMQP");
        assertThat(BusinessEndpointLabels.systemName(node("from", "from[file:src/main/data?noop=true]", null)))
                .isEqualTo("File");
        assertThat(BusinessEndpointLabels.systemName(null)).isNull();
    }

    @Test
    void endpointsWithoutADescriptionGetOne() {
        RouteInfo r = new RouteInfo();
        r.routeId = "route1";
        r.nodes.add(node("from", "from[file:src/main/data?noop=true]", null));
        r.nodes.add(node("to", "to[amqp:queue:order.queue]", null));
        r.nodes.add(node("to", "to[log:x]", "Audit trail"));
        r.nodes.add(node("log", "log[hello]", null));
        BusinessEndpointLabels.apply(List.of(r));
        assertThat(r.nodes).extracting(n -> n.description)
                .containsExactly("File: src/main/data", "AMQP: queue:order.queue", "Audit trail", null);
    }

    private static NodeInfo node(String type, String code, String description) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.code = code;
        n.description = description;
        return n;
    }
}
