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
package org.apache.camel.util;

import junit.framework.TestCase;

/**
 * Unit test for UuidGenerator
 */
public class UuidGeneratorTest extends TestCase {

    private UuidGenerator generator;

    protected void setUp() throws Exception {
        generator = new UuidGenerator("unittest");
    }

    public void testUniqueId() {
        assertNotSame("Should generate unique ids", generator.generateId(), generator.generateId());
    }

    public void testSimpleSanitizedId() {
        String out = UuidGenerator.generateSanitizedId("hello");
        assertTrue("Should not contain : ", out.indexOf(':') == -1);
        assertTrue("Should not contain . ", out.indexOf('.') == -1);
    }

    public void testNotFileFriendlySimpleSanitizedId() {
        String out = UuidGenerator.generateSanitizedId("c:\\helloworld");
        assertTrue("Should not contain : ", out.indexOf(':') == -1);
        assertTrue("Should not contain . ", out.indexOf('.') == -1);
    }

}
