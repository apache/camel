/**
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
package org.apache.camel.catalog.lucene;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CamelCatalogLuceneTest {

    private CamelCatalog catalog;

    @Before
    public void createCamelCatalog() {
        catalog = new DefaultCamelCatalog();
        catalog.setSuggestionStrategy(new LuceneSuggestionStrategy());
    }

    @Test
    public void validateProperties() throws Exception {
        // spell typo error
        EndpointValidationResult result = catalog.validateEndpointProperties("log:mylog?levl=WARN");
        assertFalse(result.isSuccess());
        assertTrue(result.getUnknown().contains("levl"));
        assertEquals("level", result.getUnknownSuggestions().get("levl")[0]);
        assertEquals(1, result.getNumberOfErrors());
    }
}
