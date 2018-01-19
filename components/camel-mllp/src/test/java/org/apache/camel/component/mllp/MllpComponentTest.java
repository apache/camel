/**
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

package org.apache.camel.component.mllp;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the  class.
 */
public class MllpComponentTest {
    Boolean initialLogPhiValue;
    Integer initialLogPhiMaxBytesValue;

    MllpComponent instance;

    @Before
    public void setUp() throws Exception {
        initialLogPhiValue = MllpComponent.logPhi;
        initialLogPhiMaxBytesValue = MllpComponent.logPhiMaxBytes;

        instance = new MllpComponent();
    }

    @After
    public void tearDown() throws Exception {
        MllpComponent.logPhi = initialLogPhiValue;
        MllpComponent.logPhiMaxBytes = initialLogPhiMaxBytesValue;
    }

    @Test
    public void testHasLogPhi() throws Exception {
        MllpComponent.logPhi = null;
        assertFalse(MllpComponent.hasLogPhi());

        MllpComponent.logPhi = false;
        assertTrue(MllpComponent.hasLogPhi());

        MllpComponent.logPhi = true;
        assertTrue(MllpComponent.hasLogPhi());
    }

    @Test
    public void testIsLogPhi() throws Exception {
        MllpComponent.logPhi = null;
        assertEquals(MllpComponent.DEFAULT_LOG_PHI, MllpComponent.isLogPhi());

        MllpComponent.logPhi = false;
        assertFalse(MllpComponent.isLogPhi());

        MllpComponent.logPhi = true;
        assertTrue(MllpComponent.isLogPhi());
    }

    @Test
    public void testSetLogPhi() throws Exception {
        MllpComponent.setLogPhi(null);
        assertNull(instance.logPhi);

        MllpComponent.setLogPhi(true);
        assertEquals(Boolean.TRUE, instance.logPhi);

        MllpComponent.setLogPhi(false);
        assertEquals(Boolean.FALSE, instance.logPhi);
    }


    @Test
    public void testHasLogPhiMaxBytes() throws Exception {
        MllpComponent.logPhiMaxBytes = null;
        assertFalse(MllpComponent.hasLogPhiMaxBytes());

        MllpComponent.logPhiMaxBytes = -1;
        assertTrue(MllpComponent.hasLogPhiMaxBytes());

        MllpComponent.logPhiMaxBytes = 1024;
        assertTrue(MllpComponent.hasLogPhiMaxBytes());
    }

    @Test
    public void testGetLogPhiMaxBytes() throws Exception {
        MllpComponent.logPhiMaxBytes = null;
        assertEquals(MllpComponent.DEFAULT_LOG_PHI_MAX_BYTES, MllpComponent.getLogPhiMaxBytes());

        int expected = -1;
        MllpComponent.logPhiMaxBytes = expected;
        assertEquals(expected, MllpComponent.getLogPhiMaxBytes());

        expected = 1024;
        MllpComponent.logPhiMaxBytes = expected;
        assertEquals(expected, MllpComponent.getLogPhiMaxBytes());
    }

    @Test
    public void testSetLogPhiMaxBytes() throws Exception {
        Integer expected = null;
        MllpComponent.setLogPhiMaxBytes(expected);
        assertEquals(expected, instance.logPhiMaxBytes);

        expected = -1;
        MllpComponent.setLogPhiMaxBytes(expected);
        assertEquals(expected, instance.logPhiMaxBytes);

        expected = 1024;
        MllpComponent.setLogPhiMaxBytes(expected);
        assertEquals(expected, instance.logPhiMaxBytes);
    }
}