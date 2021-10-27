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
package org.apache.camel.http.common;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpRestHeaderFilterStrategyTest {

    private static final Exchange NOT_USED = null;

    @Test
    public void shouldDecideOnApplingHeaderFilterToTemplateTokens() {
        final HttpRestHeaderFilterStrategy strategy = new HttpRestHeaderFilterStrategy(
                "{uriToken1}{uriToken2}",
                "q1=%7BqueryToken1%7D%26q2=%7BqueryToken2%3F%7D%26");

        assertTrue(strategy.applyFilterToCamelHeaders("uriToken1", "value", NOT_USED));
        assertTrue(strategy.applyFilterToCamelHeaders("uriToken2", "value", NOT_USED));
        assertTrue(strategy.applyFilterToCamelHeaders("queryToken1", "value", NOT_USED));
        assertTrue(strategy.applyFilterToCamelHeaders("queryToken2", "value", NOT_USED));
        assertFalse(strategy.applyFilterToCamelHeaders("unknown", "value", NOT_USED));
    }

    @Test
    public void shouldDecideOnApplingHeaderFilterToTemplateTokensUnencoded() {
        final HttpRestHeaderFilterStrategy strategy = new HttpRestHeaderFilterStrategy(
                "{uriToken1}{uriToken2}",
                "q1={queryToken1}&q2={queryToken2?}&");

        assertTrue(strategy.applyFilterToCamelHeaders("uriToken1", "value", NOT_USED));
        assertTrue(strategy.applyFilterToCamelHeaders("uriToken2", "value", NOT_USED));
        assertTrue(strategy.applyFilterToCamelHeaders("queryToken1", "value", NOT_USED));
        assertTrue(strategy.applyFilterToCamelHeaders("queryToken2", "value", NOT_USED));
        assertFalse(strategy.applyFilterToCamelHeaders("unknown", "value", NOT_USED));
    }
}
