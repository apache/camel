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

package org.apache.camel.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AnnotationHelperTest {

    @Test
    public void testGetAnnotationByFQN() {
        // no annotation
        String value =
                AnnotationHelper.getAnnotationValue(AnnotationHelperTest.class, "org.junit.jupiter.api.DisplayName");
        Assertions.assertNull(value);

        // annotation with value
        value = AnnotationHelper.getAnnotationValue(MyPojo.class, "org.junit.jupiter.api.DisplayName");
        Assertions.assertEquals("theNameHere", value);
    }

    @Test
    public void testHasAnnotationByFQN() {
        Assertions.assertFalse(
                AnnotationHelper.hasAnnotation(AnnotationHelperTest.class, "org.junit.jupiter.api.DisplayName"));
        Assertions.assertTrue(AnnotationHelper.hasAnnotation(MyPojo.class, "org.junit.jupiter.api.DisplayName"));
    }
}
