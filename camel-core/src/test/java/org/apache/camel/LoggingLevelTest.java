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
package org.apache.camel;

import junit.framework.TestCase;
import org.junit.Test;

public class LoggingLevelTest extends TestCase {

    @Test
    public void testLoggingLevelInfo() throws Exception {
        assertTrue(LoggingLevel.INFO.isEnabled(LoggingLevel.ERROR));
        assertTrue(LoggingLevel.INFO.isEnabled(LoggingLevel.WARN));
        assertTrue(LoggingLevel.INFO.isEnabled(LoggingLevel.INFO));

        assertFalse(LoggingLevel.INFO.isEnabled(LoggingLevel.DEBUG));
        assertFalse(LoggingLevel.INFO.isEnabled(LoggingLevel.TRACE));

        assertFalse(LoggingLevel.INFO.isEnabled(LoggingLevel.OFF));
    }

    @Test
    public void testLoggingLevelWARN() throws Exception {
        assertTrue(LoggingLevel.WARN.isEnabled(LoggingLevel.ERROR));
        assertTrue(LoggingLevel.WARN.isEnabled(LoggingLevel.WARN));

        assertFalse(LoggingLevel.WARN.isEnabled(LoggingLevel.INFO));
        assertFalse(LoggingLevel.WARN.isEnabled(LoggingLevel.DEBUG));
        assertFalse(LoggingLevel.WARN.isEnabled(LoggingLevel.TRACE));

        assertFalse(LoggingLevel.WARN.isEnabled(LoggingLevel.OFF));
    }
}
