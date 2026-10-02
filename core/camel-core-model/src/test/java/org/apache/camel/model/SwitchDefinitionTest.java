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
package org.apache.camel.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchDefinitionTest {
    @Test
    void readingDestinationsDoesNotOverwritePreparedNodes() {
        SwitchDefinition sw = new SwitchDefinition().id("dispatch")
                .doCase("billing", "{{case.uri}}").otherwise("{{fallback.uri}}");
        SwitchCaseDefinition c = sw.getCases().get(0);
        c.setId("billing");
        // the send node exists from the start (so its processor index follows the order of the cases), but reading
        // the model does not prepare it: that happens when the route is reified
        ToDefinition unprepared = c.getToDefinition();
        sw.getChildren();
        assertSame(unprepared, c.getToDefinition());
        assertNull(unprepared.getUri());
        assertNull(unprepared.getId());

        c.prepareToDefinition();
        sw.prepareOtherwiseDefinition();
        ToDefinition caseSend = c.getToDefinition();
        ToDefinition fallbackSend = sw.getOtherwiseDefinition();
        caseSend.setUri("mock:billing");
        caseSend.setId("prepared-billing");
        fallbackSend.setUri("mock:review");
        fallbackSend.setId("prepared-fallback");

        assertSame(caseSend, c.getToDefinition());
        assertSame(fallbackSend, sw.getOtherwiseDefinition());
        assertSame(fallbackSend, sw.getChildren().get(1));
        assertEquals("mock:billing", caseSend.getUri());
        assertEquals("prepared-billing", caseSend.getId());
        assertEquals("mock:review", fallbackSend.getUri());
        assertEquals("prepared-fallback", fallbackSend.getId());
    }

    @Test
    void caseLabelIncludesTheDestinationAndMasksSecrets() {
        SwitchCaseDefinition c = new SwitchCaseDefinition("billing", "https://example.com?password=secret");
        assertTrue(c.getLabel().startsWith("case[billing -> https://example.com?password="));
        assertFalse(c.getLabel().contains("secret"));
        assertEquals(c.getLabel(), c.toString());
        c.setUri("direct:updated");
        assertEquals("case[billing -> direct:updated]", c.getLabel());
    }

    @Test
    void copiedFallbackHasIndependentDestinationAndParent() {
        SwitchDefinition original = new SwitchDefinition().otherwise("mock:original");
        SwitchDefinition copy = original.copyDefinition();
        copy.getOtherwise().setUri("mock:copy");

        assertNotSame(original.getOtherwiseDefinition(), copy.getOtherwiseDefinition());
        assertSame(original, original.getOtherwiseDefinition().getParent());
        assertSame(copy, copy.getOtherwiseDefinition().getParent());
        assertEquals("mock:original", original.getOtherwiseDefinition().getUri());
        assertEquals("mock:copy", copy.getOtherwiseDefinition().getUri());
    }
}
