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
package org.apache.camel.component.aws2.sns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Sns2HeaderFilterStrategyTest {

    private final Sns2HeaderFilterStrategy strategy = new Sns2HeaderFilterStrategy();

    // additional 3.14.10 tests
    @Test
    void filtersMixedCaseCamelHeaders() {
        assertTrue(strategy.applyFilterToCamelHeaders("CAmeLFileName", "test.txt", null));
        assertTrue(strategy.applyFilterToCamelHeaders("CAMELHttpMethod", "GET", null));
        assertTrue(strategy.applyFilterToCamelHeaders("cAmElVersion", "3.14", null));
        assertTrue(strategy.applyFilterToCamelHeaders("ORg.Apache.Camel.", "value", null));
    }

    @Test
    void inboundFiltersMixedCaseCamelHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CAmeLFileName", "test.txt", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CAMELHttpMethod", "GET", null));
        assertFalse(strategy.applyFilterToExternalHeaders("myHeader", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("ORg.Apache.Camel.", "value", null));
    }

    // 4.x tests
    @Test
    void inboundFiltersCamelPrefixedHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CamelHttpUri", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CamelFileName", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("camelfoo", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CAMELFOO", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("org.apache.camel.", "value", null));
    }

    @Test
    void inboundAllowsNonCamelHeaders() {
        assertFalse(strategy.applyFilterToExternalHeaders("X-Custom", "value", null));
        assertFalse(strategy.applyFilterToExternalHeaders("subject", "hello", null));
        assertFalse(strategy.applyFilterToExternalHeaders("orgapachecamel.", "value", null));
    }

    @Test
    void outboundFiltersCamelAndBreadcrumbHeaders() {
        assertTrue(strategy.applyFilterToCamelHeaders("CamelHttpUri", "value", null));
        assertTrue(strategy.applyFilterToCamelHeaders("org.apache.camel.internal", "value", null));
        assertTrue(strategy.applyFilterToCamelHeaders("breadcrumbId", "value", null));
    }

    @Test
    void outboundAllowsUserHeaders() {
        assertFalse(strategy.applyFilterToCamelHeaders("X-Custom", "value", null));
    }

}


