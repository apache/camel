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
package org.apache.camel.maven.htmlxlsx.model;

import java.util.Collections;

import org.apache.camel.maven.htmlxlsx.TestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EipAttributeTest extends GetterAndSetterTest<EipAttribute> {

    private static final String DONT_CARE = "dont-care";

    private static final String SOME_VALUE = "some-value";

    @Override
    protected EipAttribute getInstance() {

        return TestUtil.eipAttribute();
    }

    @Test
    public void testToString() {

        String toString = getInstance().toString();

        assertNotNull(toString);
        assertTrue(toString.contains("EipAttribute"));
    }

    @Test
    public void testSetProperty() {

        EipAttribute attribute = getInstance();
        ChildEip childEip = TestUtil.childEip();

        attribute.setProperty(DONT_CARE, Collections.singletonList(childEip));
        assertEquals(1, attribute.getChildEipMap().get(DONT_CARE).size());

        attribute = getInstance();
        attribute.setProperty(DONT_CARE, Collections.singletonMap(DONT_CARE, childEip));
        assertEquals(1, attribute.getChildEipMap().get(DONT_CARE).size());
    }

    @Test
    public void testCompareTo() {

        EipAttribute attribute1 = getInstance();

        EipAttribute attribute2 = getInstance();

        int result = attribute1.compareTo(attribute2);
        assertEquals(0, result);

        result = attribute1.compareTo(null);
        assertNotEquals(0, result);

        attribute2.setIndex(5);
        result = attribute1.compareTo(attribute2);
        assertNotEquals(0, result);
    }

    @Test
    public void testEquals() {

        EipAttribute attribute1 = getInstance();

        EipAttribute attribute2 = getInstance();

        boolean result = attribute1.equals(attribute1);
        assertTrue(result);

        result = attribute1.equals(attribute2);
        assertTrue(result);

        result = attribute1.equals(null);
        assertFalse(result);

        attribute2.setId(SOME_VALUE);
        result = attribute1.equals(attribute2);
        assertFalse(result);
    }
}
