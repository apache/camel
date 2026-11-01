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
package org.apache.camel.catalog;

import java.util.List;

import org.apache.camel.tooling.model.DstWorldModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fake worlds of the deterministic simulation tests in the catalog: the dstWorlds section of the components, and
 * the table of worlds in the camel-test-dst page generated from it (CAMEL-26438).
 */
class CamelCatalogDstWorldsTest {

    static CamelCatalog catalog;

    @BeforeAll
    static void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @ParameterizedTest
    @CsvSource({
            "aws2-sqs,SqsWorld,SqsRedeliveryDstTest SqsVisibilityDstTest",
            "azure-eventhubs,EventHubsWorld,EventHubsCheckpointDstTest",
            "kafka-share,KafkaShareWorld,KafkaShareConsumerDstTest",
            "pulsar,PulsarWorld,PulsarConsumerDstTest",
            "slack,SlackWorld,SlackSuspendDstTest" })
    void testComponentWithWorld(String scheme, String world, String tests) {
        List<DstWorldModel> worlds = catalog.componentModel(scheme).getDstWorlds();
        assertEquals(List.of(world), worlds.stream().map(DstWorldModel::getName).toList());
        DstWorldModel w = worlds.get(0);
        assertTrue(w.getJavaType().endsWith(".dst." + world), w.getJavaType());
        assertEquals(List.of(tests.split(" ")), w.getTests());
        assertTrue(!w.getFake().isBlank() && !w.getCovers().isEmpty() && !w.getFaults().isEmpty()
                && !w.getInvariants().isEmpty() && !w.getIssues().isEmpty(), world);
    }

    @Test
    void testSqsWorld() {
        DstWorldModel w = catalog.componentModel("aws2-sqs").getDstWorlds().get(0);
        assertEquals("org.apache.camel.component.aws2.sqs.dst.SqsWorld", w.getJavaType());
        assertTrue(w.getInvariants().contains("no message is delivered again while the consumer holds it"),
                w.getInvariants().toString());
        assertEquals(List.of("CAMEL-25700", "CAMEL-25737", "CAMEL-25738", "CAMEL-25911"), w.getIssues());
    }

    @Test
    void testTableOfWorldsInTheDocMatchesTheCatalog() {
        String doc = catalog.otherAsciiDoc("test-dst");
        String table = doc.substring(doc.indexOf("// dst worlds: START"), doc.indexOf("// dst worlds: END"));
        List<String> rows = table.lines().filter(l -> l.startsWith("|`")).toList();
        int worlds = 0;
        for (String name : catalog.findComponentNames()) {
            for (DstWorldModel w : catalog.componentModel(name).getDstWorlds()) {
                assertTrue(rows.stream().anyMatch(r -> r.startsWith("|`" + w.getName() + "`")),
                        w.getName() + " is not in the table of test-dst.adoc: " + rows);
                worlds++;
            }
        }
        assertEquals(worlds, rows.size(), "the table of test-dst.adoc does not match the catalog: " + rows);
    }
}
