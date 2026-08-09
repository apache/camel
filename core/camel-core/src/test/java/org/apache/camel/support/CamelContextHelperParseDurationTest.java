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
package org.apache.camel.support;

import java.time.Duration;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelContextHelperParseDurationTest extends ContextTestSupport {

    @Test
    void testParseDurationMillis() {
        Duration d = CamelContextHelper.parseDuration(context, "20000");
        assertThat(d).isEqualTo(Duration.ofMillis(20000));
    }

    @Test
    void testParseDurationHumanReadable() {
        Duration d = CamelContextHelper.parseDuration(context, "20s");
        assertThat(d).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void testParseDurationHumanReadableMinutes() {
        Duration d = CamelContextHelper.parseDuration(context, "1m30s");
        assertThat(d).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void testParseDurationISO8601() {
        Duration d = CamelContextHelper.parseDuration(context, "PT20S");
        assertThat(d).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void testParseDurationISO8601Lowercase() {
        Duration d = CamelContextHelper.parseDuration(context, "pt20s");
        assertThat(d).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void testParseDurationNull() {
        Duration d = CamelContextHelper.parseDuration(context, null);
        assertThat(d).isNull();
    }
}
