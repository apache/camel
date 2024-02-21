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
package org.apache.camel.component.atom;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class AtomHttpNoCamelParametersTest extends CamelTestSupport {

    @Test
    void testAtomHttpNoCamelParameters() {
        AtomEndpoint atom = context.getEndpoint(
                "atom://http://www.iafrica.com/pls/cms/grapevine.xml?sortEntries=true&feedHeader=true", AtomEndpoint.class);
        assertNotNull(atom);

        assertEquals("http://www.iafrica.com/pls/cms/grapevine.xml", atom.getFeedUri());
        assertTrue(atom.isFeedHeader());
        assertTrue(atom.isSortEntries());
    }

    @Test
    void testAtomHttpNoCamelParametersAndOneFeedParameter() {
        AtomEndpoint atom = context.getEndpoint(
                "atom://http://www.iafrica.com/pls/cms/grapevine.xml?sortEntries=true&feedHeader=true&foo=bar",
                AtomEndpoint.class);
        assertNotNull(atom);

        assertEquals("http://www.iafrica.com/pls/cms/grapevine.xml?foo=bar", atom.getFeedUri());
        assertTrue(atom.isFeedHeader());
        assertTrue(atom.isSortEntries());
    }

}
