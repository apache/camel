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
package org.apache.camel.component.salesforce.api.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionTest {

    private static final Version V34_0 = Version.create("34.0");

    private static final Version V34_3 = Version.create("34.3");

    private static final Version V35_0 = Version.create("35.0");

    @Test
    public void shouldCreate() {
        final Version version = V34_3;

        assertEquals(34, version.getMajor());
        assertEquals(3, version.getMinor());
    }

    @Test
    public void shouldObserveApiLimits() {
        V34_0.requireAtLeast(34, 0);
        V34_0.requireAtLeast(33, 9);
        V35_0.requireAtLeast(34, 0);
    }

    @Test
    public void shouldObserveApiLimitsOnMajorVersions() {
        assertThrows(UnsupportedOperationException.class,
                () -> V35_0.requireAtLeast(36, 0));
    }

    @Test
    public void shouldObserveApiLimitsOnMinorVersions() {
        assertThrows(UnsupportedOperationException.class,
                () -> V35_0.requireAtLeast(35, 1));
    }

    @Test
    public void testComparator() {
        assertTrue(V34_0.compareTo(V34_3) < 0);
        assertTrue(V34_0.compareTo(V35_0) < 0);
        assertTrue(V34_3.compareTo(V35_0) < 0);

        assertTrue(V34_3.compareTo(V34_0) > 0);
        assertTrue(V35_0.compareTo(V34_0) > 0);
        assertTrue(V35_0.compareTo(V34_3) > 0);

        assertEquals(0, V34_0.compareTo(V34_0));
        assertEquals(0, V34_3.compareTo(V34_3));
        assertEquals(0, V35_0.compareTo(V35_0));
    }
}
