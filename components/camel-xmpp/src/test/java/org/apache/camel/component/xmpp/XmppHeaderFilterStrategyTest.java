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
package org.apache.camel.component.xmpp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmppHeaderFilterStrategyTest {

    private final XmppHeaderFilterStrategy strategy = new XmppHeaderFilterStrategy();

    @Test
    void inboundCamelHeadersAreFiltered() {
        assertTrue(strategy.applyFilterToExternalHeaders("CamelHttpUri", "http://evil.example", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CamelFileName", "../../etc/passwd", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CamelBeanMethodName", "evilMethod", null));
    }

    @Test
    void inboundLowercaseCamelHeadersAreFiltered() {
        assertTrue(strategy.applyFilterToExternalHeaders("camelHttpUri", "http://evil.example", null));
        assertTrue(strategy.applyFilterToExternalHeaders("camelfilename", "../../etc/passwd", null));
    }

    @Test
    void outboundCamelHeadersAreFiltered() {
        assertTrue(strategy.applyFilterToCamelHeaders("CamelHttpUri", "value", null));
        assertTrue(strategy.applyFilterToCamelHeaders("camelHttpUri", "value", null));
    }

    @Test
    void nonCamelHeadersPassThrough() {
        assertFalse(strategy.applyFilterToExternalHeaders("Content-Type", "application/json", null));
        assertFalse(strategy.applyFilterToExternalHeaders("X-Request-Id", "abc-123", null));
        assertFalse(strategy.applyFilterToCamelHeaders("Content-Type", "application/json", null));
    }
}
