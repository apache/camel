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
package org.apache.camel.xml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25771: the hash an endpoint DSL builder adds for its options that are objects is an identity hash, another one
 * in every run, so the checksum of a route leaves it out.
 */
class LwModelToXMLDumperRawUriTest {

    @Test
    void theHashOfTheObjectOptionsIsLeftOutOfTheChecksum() {
        assertEquals("sql:select?a=1&b=2", LwModelToXMLDumper.rawUri("sql:select?a=1&hash=1f2e3d&b=2", true));
        assertEquals("sql:select?b=2", LwModelToXMLDumper.rawUri("sql:select?hash=1f2e3d&b=2", true));
        assertEquals("sql:select?a=1", LwModelToXMLDumper.rawUri("sql:select?a=1&hash=1f2e3d", true));
        assertEquals("sql:select", LwModelToXMLDumper.rawUri("sql:select?hash=1f2e3d", true));
        assertEquals("sql:select?ahash=1f2e3d", LwModelToXMLDumper.rawUri("sql:select?ahash=1f2e3d", true));
    }

    @Test
    void theDumpKeepsTheHash() {
        assertEquals("sql:select?a=1&hash=1f2e3d", LwModelToXMLDumper.rawUri("sql:select?a=1&hash=1f2e3d", false));
    }
}
