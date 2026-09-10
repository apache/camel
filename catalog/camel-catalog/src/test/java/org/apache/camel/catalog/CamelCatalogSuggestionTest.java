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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.camel.catalog.impl.EditDistanceSuggestionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog suggests the closest option names and enum values out of the box (edit distance based).
 */
public class CamelCatalogSuggestionTest {

    private final CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    public void suggestsByDefault() {
        assertNotNull(catalog.getSuggestionStrategy());
        assertTrue(catalog.getSuggestionStrategy() instanceof EditDistanceSuggestionStrategy);
    }

    @Test
    public void typoInOptionName() {
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?levl=WARN");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("levl"));
        assertEquals("level", result.getUnknownSuggestions().get("levl")[0]);
        assertTrue(result.summaryErrorMessage(false).contains("Did you mean: [level]"));
    }

    @Test
    public void wrongCaseAndSwappedLetters() {
        EndpointValidationResult result = catalog.validateEndpointProperties("kafka:orders?groupid=demo&brokres=x");
        assertEquals("groupId", result.getUnknownSuggestions().get("groupid")[0]);
        assertEquals("brokers", result.getUnknownSuggestions().get("brokres")[0]);
    }

    @Test
    public void partlyTypedNameListsTheNamesStartingWithIt() {
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?showE=true");
        assertArrayEquals(new String[] { "showException", "showExchangeId", "showExchangePattern" },
                result.getUnknownSuggestions().get("showE"));
    }

    @Test
    public void nothingCloseGivesNoSuggestion() {
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?xyzzyqwerty=true");
        assertEquals(0, result.getUnknownSuggestions().get("xyzzyqwerty").length);
        assertTrue(result.summaryErrorMessage(false).contains("Unknown option"));
        assertFalse(result.summaryErrorMessage(false).contains("Did you mean"));
    }

    @Test
    public void invalidEnumValueSuggestsTheClosestChoice() {
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?level=WARM");
        assertFalse(result.isSuccess());
        assertEquals("WARM", result.getInvalidEnum().get("level"));
        assertEquals("WARN", result.getInvalidEnumSuggestions().get("level")[0]);
    }

    @Test
    public void suggestionsCanBeTurnedOff() {
        CamelCatalog silent = new DefaultCamelCatalog();
        silent.setSuggestionStrategy(null);
        EndpointValidationResult result = silent.validateEndpointProperties("log:mylog?levl=WARN");
        assertTrue(result.getUnknown().contains("levl"));
        assertNull(result.getUnknownSuggestions());
    }

    @Test
    public void strategyRanksExactThenPrefixThenDistance() {
        List<String> names = Arrays.asList("period", "fixedRate", "delay", "repeatCount", "timerName", "time");
        assertArrayEquals(new String[] { "fixedRate" }, EditDistanceSuggestionStrategy.suggest(names, "fixedRte", 5));
        assertArrayEquals(new String[] { "period" }, EditDistanceSuggestionStrategy.suggest(names, "PERIOD", 5));
        // "tim" is a prefix of both; the shorter name sorts first alphabetically
        assertArrayEquals(new String[] { "time", "timerName" }, EditDistanceSuggestionStrategy.suggest(names, "tim", 5));
        assertArrayEquals(new String[] { "delay" }, EditDistanceSuggestionStrategy.suggest(names, "dealy", 5));
        assertEquals(0, EditDistanceSuggestionStrategy.suggest(names, "brokers", 5).length);
        assertEquals(0, EditDistanceSuggestionStrategy.suggest(names, "", 5).length);
        assertEquals(0, EditDistanceSuggestionStrategy.suggest(null, "period", 5).length);
        assertEquals(1, EditDistanceSuggestionStrategy.suggest(names, "tim", 1).length);
        assertEquals(1, new EditDistanceSuggestionStrategy().suggestEndpointOptions(Set.of("period"), "perod").length);
    }
}
