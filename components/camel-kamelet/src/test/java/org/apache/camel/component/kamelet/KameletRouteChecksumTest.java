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
package org.apache.camel.component.kamelet;

import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CAMEL-25771: a Kamelet route gets its node prefix, and its id when it has none of its own, from a counter, which the
 * checksum leaves out: the same Kamelet route has the same checksum each time it is created.
 */
class KameletRouteChecksumTest {

    @Test
    void theSameKameletRouteHasTheSameChecksumWhenCreatedAgain() throws Exception {
        // the counter goes on in the second run, as it does when a reload creates the routes again
        Map<String, String> first = checksums();
        Map<String, String> second = checksums();

        assertEquals(2, first.size(), first.toString());
        first.values().forEach(checksum -> assertNotNull(checksum, first.toString()));
        assertEquals(first, second);
    }

    // the checksums of the Kamelet routes: the one with an id of its own, and the one without
    private static Map<String, String> checksums() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("greeting").templateParameter("name")
                            .from("kamelet:source")
                            .setBody().simple("Hello {{name}}");

                    from("direct:own").routeId("own").kamelet("greeting/greet-own?name=own");
                    from("direct:none").routeId("none").kamelet("greeting?name=none");
                }
            });
            context.start();
            Map<String, String> answer = new TreeMap<>();
            for (Route route : context.getRoutes()) {
                if ("greeting".equals(route.getRouteTemplateId())) {
                    answer.put("greet-own".equals(route.getId()) ? "own" : "none", route.getChecksum());
                }
            }
            return answer;
        }
    }
}
