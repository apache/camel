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

    @Test
    void inboundFiltersCamelPrefixedHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CamelHttpUri", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CamelFileName", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("camelfoo", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CAMELFOO", "value", null));
    }

    @Test
    void inboundAllowsNonCamelHeaders() {
        assertFalse(strategy.applyFilterToExternalHeaders("X-Custom", "value", null));
        assertFalse(strategy.applyFilterToExternalHeaders("subject", "hello", null));
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
