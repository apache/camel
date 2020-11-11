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
package org.apache.camel.maven.config;

import java.time.Duration;

import org.apache.camel.util.TimeUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConnectorConfigGeneratorUtilsTest {

    @Test
    public void testToTimeAsString() {
        assertEquals("600ms", ConnectorConfigGeneratorUtils.toTimeAsString(TimeUtils.toMilliSeconds("600ms")));
        assertEquals("0ms", ConnectorConfigGeneratorUtils.toTimeAsString(TimeUtils.toMilliSeconds("0ms")));
        assertEquals("1s", ConnectorConfigGeneratorUtils.toTimeAsString(TimeUtils.toMilliSeconds("1000ms")));
        assertEquals("1m600ms", ConnectorConfigGeneratorUtils.toTimeAsString(TimeUtils.toMilliSeconds("1m600ms")));
        assertEquals("1m1s100ms", ConnectorConfigGeneratorUtils.toTimeAsString(TimeUtils.toMilliSeconds("1m1100ms")));
        assertEquals("5m10s300ms", ConnectorConfigGeneratorUtils.toTimeAsString(310300));
        assertEquals("5s500ms", ConnectorConfigGeneratorUtils.toTimeAsString(5500));
        assertEquals("1h50m", ConnectorConfigGeneratorUtils.toTimeAsString(6600000));
        assertEquals("2d3h4m", ConnectorConfigGeneratorUtils.toTimeAsString(Duration.parse("P2DT3H4M").toMillis()));
        assertEquals("2d4m", ConnectorConfigGeneratorUtils.toTimeAsString(Duration.parse("P2DT4M").toMillis()));
    }
}
