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
package org.apache.camel.component.ganglia;

import java.net.URI;
import java.net.URISyntaxException;

import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetric.UDPAddressingMode;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit test class for
 * <code>org.apache.camel.component.ganglia.GangliaConfiguration</code>
 */
public class GangliaConfigurationTest {

    private GangliaConfiguration configuration;

    @BeforeEach
    public void setUp() {
        configuration = new GangliaConfiguration();
    }

    @Test
    public void getHostShouldReturnDefaultValue() {
        assertEquals("239.2.11.71", configuration.getHost());
    }

    @Test
    public void getPortShouldReturnDefaultValue() {
        assertEquals(8649, configuration.getPort());
    }

    @Test
    public void getModeShouldReturnDefaultValue() {
        assertEquals(GMetric.UDPAddressingMode.MULTICAST, configuration.getMode());
    }

    @Test
    public void getTtlShouldReturnDefaultValue() {
        assertEquals(5, configuration.getTtl());
    }

    @Test
    public void getWireFormat31xShouldReturnDefaultValue() {
        assertEquals(true, configuration.getWireFormat31x());
    }

    @Test
    public void getSpoofHostnameShouldReturnDefaultValue() {
        assertEquals(null, configuration.getSpoofHostname());
    }

    @Test
    public void getGroupNameShouldReturnDefaultValue() {
        assertEquals("Java", configuration.getGroupName());
    }

    @Test
    public void getPrefixShouldReturnDefaultValue() {
        assertEquals(null, configuration.getPrefix());
    }

    @Test
    public void getMetricNameShouldReturnDefaultValue() {
        assertEquals("metric", configuration.getMetricName());
    }

    @Test
    public void getTypeShouldReturnDefaultValue() {
        assertEquals(GMetricType.STRING, configuration.getType());
    }

    @Test
    public void getSlopeShouldReturnDefaultValue() {
        assertEquals(GMetricSlope.BOTH, configuration.getSlope());
    }

    @Test
    public void getUnitsShouldReturnDefaultValue() {
        assertEquals("", configuration.getUnits());
    }

    @Test
    public void isWireFormat31xShouldReturnDefaultValue() {
        assertTrue(configuration.isWireFormat31x());
    }

    @Test
    public void getTMaxShouldReturnDefaultValue() {
        assertEquals(60, configuration.getTmax());
    }

    @Test
    public void getDMaxShouldReturnDefaultValue() {
        assertEquals(0, configuration.getDmax());
    }

    @Test
    public void toStringShouldSucceed() {
        assertNotNull(configuration.toString());
    }

    @Test
    public void configureShouldSetHostAndPort() throws URISyntaxException {
        configuration.configure(new URI("ganglia://192.168.1.1:28649"));
        assertEquals("192.168.1.1", configuration.getHost());
        assertEquals(28649, configuration.getPort());
    }

    @Test
    public void configureWithoutHostShouldKeepDefaultHost() throws URISyntaxException {
        configuration.configure(new URI("ganglia://:28649"));
        assertEquals("239.2.11.71", configuration.getHost());
    }

    @Test
    public void configureWithoutPortShouldKeepDefaultPort() throws URISyntaxException {
        configuration.configure(new URI("ganglia://192.168.1.1:"));
        assertEquals(8649, configuration.getPort());
    }

    @Test
    public void cloneShouldSucceed() {
        GangliaConfiguration clone = configuration.copy();
        assertEquals("239.2.11.71", clone.getHost());
        assertEquals(8649, clone.getPort());
        assertEquals(UDPAddressingMode.MULTICAST, clone.getMode());
        assertEquals(5, clone.getTtl());
        assertEquals(true, clone.getWireFormat31x());
        assertEquals(null, clone.getSpoofHostname());
        assertEquals("Java", clone.getGroupName());
        assertEquals(null, clone.getPrefix());
        assertEquals("metric", clone.getMetricName());
        assertEquals(GMetricType.STRING, clone.getType());
        assertEquals(GMetricSlope.BOTH, clone.getSlope());
        assertEquals("", clone.getUnits());
        assertTrue(clone.isWireFormat31x());
        assertEquals(60, clone.getTmax());
        assertEquals(0, clone.getDmax());
    }
}
