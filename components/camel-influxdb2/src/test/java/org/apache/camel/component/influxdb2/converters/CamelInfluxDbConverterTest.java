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
package org.apache.camel.component.influxdb2.converters;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.influxdb.client.write.Point;
import org.apache.camel.component.influxdb2.InfluxDb2Constants;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelInfluxDbConverterTest {

    private static final Logger LOG = LoggerFactory.getLogger(CamelInfluxDbConverterTest.class);

    @Test
    public void doesNotAddCamelHeaders() {
        Map<String, Object> pointInMapFormat = new HashMap<>();
        pointInMapFormat.put(InfluxDb2Constants.MEASUREMENT, "testCPU");
        double value = 99.999999d;
        pointInMapFormat.put("busy", value);

        Point p = CamelInfluxDb2Converters.fromMapToPoint(pointInMapFormat);
        assertNotNull(p);

        String line = p.toLineProtocol();

        assertNotNull(line);
        LOG.debug("doesNotAddCamelHeaders generated: \"{}\"", line);
        assertFalse(line.contains(InfluxDb2Constants.MEASUREMENT));

    }

    @Test
    public void canAddDouble() {
        Map<String, Object> pointInMapFormat = new HashMap<>();
        pointInMapFormat.put(InfluxDb2Constants.MEASUREMENT, "testCPU");
        double value = 99.999999d;
        pointInMapFormat.put("busy", value);

        Point p = CamelInfluxDb2Converters.fromMapToPoint(pointInMapFormat);
        assertNotNull(p);

        String line = p.toLineProtocol();

        assertNotNull(line);
        LOG.debug("Doublecommand generated: \"{}\"", line);
        assertTrue(line.contains("busy=99.999999"));

    }

    @Test
    public void canAddInt() {
        Map<String, Object> pointInMapFormat = new HashMap<>();
        pointInMapFormat.put(InfluxDb2Constants.MEASUREMENT, "testCPU");
        int value = 99999999;
        pointInMapFormat.put("busy", value);

        Point p = CamelInfluxDb2Converters.fromMapToPoint(pointInMapFormat);
        assertNotNull(p);

        String line = p.toLineProtocol();

        assertNotNull(line);
        LOG.debug("Int command generated: \"{}\"", line);
        assertTrue(line.contains("busy=99999999"));

    }

    @Test
    void convertsAnImmutableMap() {
        Map<String, Object> pointInMapFormat = Map.of(InfluxDb2Constants.MEASUREMENT, "testCPU", "busy", 99);

        Point p = CamelInfluxDb2Converters.fromMapToPoint(pointInMapFormat);

        assertEquals("testCPU busy=99i", p.toLineProtocol());
    }

    @Test
    void doesNotChangeTheMapOfTheCaller() {
        Map<String, Object> pointInMapFormat = new LinkedHashMap<>();
        pointInMapFormat.put(InfluxDb2Constants.MEASUREMENT, "testCPU");
        pointInMapFormat.put("busy", 99);
        Map<String, Object> before = new LinkedHashMap<>(pointInMapFormat);

        Point p = CamelInfluxDb2Converters.fromMapToPoint(pointInMapFormat);

        assertEquals("testCPU busy=99i", p.toLineProtocol());
        assertEquals(before, pointInMapFormat);
        // the entries are in the same order: the measurement name was not removed and put back at the end
        assertEquals(List.copyOf(before.keySet()), List.copyOf(pointInMapFormat.keySet()));
    }

    @Test
    public void canAddByte() {
        Map<String, Object> pointInMapFormat = new HashMap<>();
        pointInMapFormat.put(InfluxDb2Constants.MEASUREMENT, "testCPU");
        byte value = Byte.MAX_VALUE;
        pointInMapFormat.put("busy", value);

        Point p = CamelInfluxDb2Converters.fromMapToPoint(pointInMapFormat);
        assertNotNull(p);

        String line = p.toLineProtocol();

        assertNotNull(line);
        LOG.debug("Byte command generated: \"{}\"", line);
        assertTrue(line.contains("busy=127"));

    }
}
