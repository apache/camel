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
package org.apache.camel.language.xtokenizer;

import javax.xml.namespace.QName;

import org.apache.camel.language.xtokenizer.XMLTokenExpressionIterator.AttributedQName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AttributedQNameTest {

    @Test
    public void testMatches() {
        AttributedQName aqname = new AttributedQName("urn:foo", "petra");
        assertTrue(aqname.matches(new QName("urn:foo", "petra")));
        assertFalse(aqname.matches(new QName("urn:bar", "petra")));
        assertFalse(aqname.matches(new QName("urn:foo", "petira")));

        aqname = new AttributedQName("urn:foo", "*tra");
        assertTrue(aqname.matches(new QName("urn:foo", "petra")));
        assertTrue(aqname.matches(new QName("urn:foo", "astra")));
        assertFalse(aqname.matches(new QName("urn:foo", "sandra")));

        aqname = new AttributedQName("urn:foo", "pe*");
        assertTrue(aqname.matches(new QName("urn:foo", "petra")));
        assertFalse(aqname.matches(new QName("urn:foo", "astra")));
        assertTrue(aqname.matches(new QName("urn:foo", "peach")));
        assertTrue(aqname.matches(new QName("urn:foo", "peteria")));

        aqname = new AttributedQName("urn:foo", "p*t*a");
        assertTrue(aqname.matches(new QName("urn:foo", "petra")));
        assertFalse(aqname.matches(new QName("urn:foo", "astra")));
        assertFalse(aqname.matches(new QName("urn:foo", "pesandra")));
        assertTrue(aqname.matches(new QName("urn:foo", "patricia")));

        aqname = new AttributedQName("urn:foo", "p?t?a");
        assertTrue(aqname.matches(new QName("urn:foo", "petra")));
        assertTrue(aqname.matches(new QName("urn:foo", "patia")));
        assertFalse(aqname.matches(new QName("urn:foo", "patricia")));

        aqname = new AttributedQName("urn:foo", "de.petra");
        assertTrue(aqname.matches(new QName("urn:foo", "de.petra")));

        aqname = new AttributedQName("urn:foo", "de.pe*");
        assertTrue(aqname.matches(new QName("urn:foo", "de.petra")));
        assertTrue(aqname.matches(new QName("urn:foo", "de.peach")));
        assertFalse(aqname.matches(new QName("urn:foo", "delpeach")));

        // matches any namespaces qualified and unqualified
        aqname = new AttributedQName("*", "p*a");
        assertTrue(aqname.matches(new QName("urn:foo", "petra")));
        assertTrue(aqname.matches(new QName("urn:bar", "patia")));
        assertTrue(aqname.matches(new QName("", "patricia")));
        assertFalse(aqname.matches(new QName("urn:bar", "peach")));
    }
}
