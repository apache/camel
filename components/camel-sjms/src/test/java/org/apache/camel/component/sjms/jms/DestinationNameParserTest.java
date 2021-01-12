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
package org.apache.camel.component.sjms.jms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DestinationNameParserTest {

    @Test
    public void testIsTopic() throws Exception {
        assertTrue(DestinationNameParser.isTopic("topic:foo"));
        assertFalse(DestinationNameParser.isTopic("queue:bar"));
        assertFalse(DestinationNameParser.isTopic("bar"));
    }

    @Test
    public void testIsTopicNullDestinationName() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> DestinationNameParser.isTopic(null));
    }

    @Test
    public void testGetShortName() throws Exception {
        assertEquals("foo", DestinationNameParser.getShortName("topic:foo"));
        assertFalse(DestinationNameParser.isTopic("queue:bar"), "bar");
        assertFalse(DestinationNameParser.isTopic("bar"), "bar");
    }

    @Test
    public void testGetShortNameNullDestinationName() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> DestinationNameParser.getShortName(null));
    }

}
