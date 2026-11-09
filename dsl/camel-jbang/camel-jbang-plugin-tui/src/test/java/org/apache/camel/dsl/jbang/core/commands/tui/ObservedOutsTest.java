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

import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The systems an integration sent to that its routes do not name, as the http-client example has them: check-stock
 * calls the stock service with a toD per SKU, and stock-service answers its REST calls.
 */
class ObservedOutsTest {

    @Test
    void theUrisAToDBuiltForOneServiceAreOneLine() {
        IntegrationInfo info = new IntegrationInfo();
        info.endpoints.add(endpoint("check-stock", "in", "file://orders?noop=true", "remote", 3));
        info.endpoints.add(endpoint("check-stock", "out",
                "http://localhost:8080/stock/CAMEL-MUG?throwExceptionOnFailure=false", "remote", 3));
        info.endpoints.add(endpoint("check-stock", "out",
                "http://localhost:8080/stock/CAMEL-TSHIRT?throwExceptionOnFailure=false", "remote", 2));
        info.endpoints.add(endpoint("check-stock", "out",
                "http://localhost:8080/stock/CAMEL-CAP?throwExceptionOnFailure=false", "remote", 1));
        // the reply of the REST route is no system it sends to
        info.endpoints.add(endpoint("stock-service", "in", "platform-http:///stock/%7Bsku%7D", "remote", 6));
        info.endpoints.add(endpoint("stock-service", "out", "platform-http:///stock/%7Bsku%7D", "remote", 6));
        // nor a call to another route, or a log
        info.endpoints.add(endpoint("check-stock", "out", "direct://picking", "internal", 3));

        List<About.Out> outs = ObservedOuts.of(info, List.of());

        assertThat(outs).extracting(About.Out::category, About.Out::uri, About.Out::route)
                .containsExactly(tuple("http", "http://localhost:8080/stock/…", "check-stock"));
        // the line has the calls of the three URIs
        assertThat(AboutView.outHits(info, outs.get(0).uri(), "check-stock")).isEqualTo(6);
    }

    @Test
    void anOutTheRoutesNameIsNotTwice() {
        IntegrationInfo info = new IntegrationInfo();
        info.endpoints.add(endpoint("audit", "out", "kafka:audit?brokers=localhost:9092", "remote", 4));

        assertThat(ObservedOuts.of(info, List.of(new About.Out("messaging", "kafka:audit", "out", "audit")))).isEmpty();
        // one URI of a service is its own line
        assertThat(ObservedOuts.of(info, List.of())).extracting(About.Out::uri).containsExactly("kafka:audit");
    }

    @Test
    void theServiceOfAUriIsAllButItsLastSegment() {
        assertThat(ObservedOuts.group("http://localhost:8080/stock/CAMEL-MUG")).isEqualTo("http://localhost:8080/stock/");
        assertThat(ObservedOuts.group("http://localhost:8080")).isEqualTo("http://localhost:8080");
        assertThat(ObservedOuts.group("kafka:audit")).isEqualTo("kafka:audit");
    }

    private static EndpointInfo endpoint(String routeId, String direction, String uri, String scope, long hits) {
        EndpointInfo ep = new EndpointInfo();
        ep.routeId = routeId;
        ep.direction = direction;
        ep.uri = uri;
        ep.scope = scope;
        ep.hits = hits;
        return ep;
    }
}
