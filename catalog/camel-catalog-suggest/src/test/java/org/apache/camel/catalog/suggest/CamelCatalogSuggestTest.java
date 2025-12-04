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

package org.apache.camel.catalog.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CamelCatalogSuggestTest {

    private CamelCatalog catalog;

    @BeforeAll
    public void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
        catalog.setSuggestionStrategy(new CatalogSuggestionStrategy());
    }

    @Test
    public void validatePropertiesTypo() {
        // spell typo error
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?levl=WARN");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("levl"));
        assertEquals("level", result.getUnknownSuggestions().get("levl")[0]);
        assertEquals(1, result.getNumberOfErrors());
    }

    @Test
    public void validatePropertiesUnknown() {
        // spell typo error
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?showE=true");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("showE"));
        assertEquals(4, result.getUnknownSuggestions().get("showE").length);
        assertEquals("showAll", result.getUnknownSuggestions().get("showE")[0]);
        assertEquals("showException", result.getUnknownSuggestions().get("showE")[1]);
        assertEquals("showExchangeId", result.getUnknownSuggestions().get("showE")[2]);
        assertEquals("showExchangePattern", result.getUnknownSuggestions().get("showE")[3]);
        assertEquals(1, result.getNumberOfErrors());
    }
}
