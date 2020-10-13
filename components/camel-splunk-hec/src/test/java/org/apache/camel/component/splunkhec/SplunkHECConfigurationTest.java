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
package org.apache.camel.component.splunkhec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SplunkHECConfigurationTest {

    @Test
    public void testHostDefaultIsNotNull() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        assertNotNull(config.getHost());
    }

    @Test
    public void testHostSet() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        config.setHost("mine");
        assertEquals("mine", config.getHost());
    }

    @Test
    public void testDefaultIndex() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        assertEquals("camel", config.getIndex());
    }

    @Test
    public void testDefaultSource() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        assertEquals("camel", config.getSource());
    }

    @Test
    public void testDefaultSourceType() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        assertEquals("camel", config.getSourceType());
    }

    @Test
    public void testDefaultSkipTlsVerifyIsFalse() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        assertEquals(false, config.isSkipTlsVerify());
    }

    @Test
    public void testDefaultHttps() {
        SplunkHECConfiguration config = new SplunkHECConfiguration();
        assertEquals(true, config.isHttps());
    }
}
