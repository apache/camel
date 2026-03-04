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
package org.apache.camel.component.micrometer.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractMicrometerServiceMatchFilterTest {

    @Test
    void exactMatchReturnsTrue() {
        assertTrue(AbstractMicrometerService.matchesFilter("app.info", "app.info"));
    }

    @Test
    void exactMismatchReturnsFalse() {
        assertFalse(AbstractMicrometerService.matchesFilter("app.info", "other.metric"));
    }

    @Test
    void wildcardMatchReturnsTrue() {
        assertTrue(AbstractMicrometerService.matchesFilter("camel.exchanges.completed", "camel.exchanges.*"));
        assertTrue(AbstractMicrometerService.matchesFilter("camel.exchanges.failed", "camel.exchanges.*"));
    }

    @Test
    void wildcardMismatchReturnsFalse() {
        assertFalse(AbstractMicrometerService.matchesFilter("camel.routes.completed", "camel.exchanges.*"));
    }

    @Test
    void multipleFiltersAnyMatchReturnsTrue() {
        assertTrue(AbstractMicrometerService.matchesFilter("app.info", "foo.*", "app.info"));
        assertTrue(AbstractMicrometerService.matchesFilter("camel.exchanges.completed", "foo.*", "camel.exchanges.*"));
    }

    @Test
    void multipleFiltersNoMatchReturnsFalse() {
        assertFalse(AbstractMicrometerService.matchesFilter("random.metric", "foo.*", "app.info"));
    }

    @Test
    void emptyFiltersReturnsFalse() {
        assertFalse(AbstractMicrometerService.matchesFilter("anything"));
    }
}
