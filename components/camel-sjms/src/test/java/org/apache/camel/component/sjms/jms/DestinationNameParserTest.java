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

/**
 * @author jkorab
 */
public class DestinationNameParserTest {

    @Test
    public void testIsTopic() throws Exception {
        DestinationNameParser parser = new DestinationNameParser();
        assertTrue(parser.isTopic("topic:foo"));
        assertFalse(parser.isTopic("queue:bar"));
        assertFalse(parser.isTopic("bar"));
    }

    @Test
    public void testIsTopicNullDestinationName() throws Exception {
        DestinationNameParser parser = new DestinationNameParser();
        assertThrows(IllegalArgumentException.class,
                () -> parser.isTopic(null));
    }

    @Test
    public void testGetShortName() throws Exception {
        DestinationNameParser parser = new DestinationNameParser();
        assertEquals("foo", parser.getShortName("topic:foo"));
        assertFalse(parser.isTopic("queue:bar"), "bar");
        assertFalse(parser.isTopic("bar"), "bar");
    }

    @Test
    public void testGetShortNameNullDestinationName() throws Exception {
        DestinationNameParser parser = new DestinationNameParser();
        assertThrows(IllegalArgumentException.class,
                () -> parser.getShortName(null));
    }

}
