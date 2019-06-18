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
package org.apache.camel.converter.jaxp;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.xml.BytesSource;
import org.junit.Test;

public class BytesSourceTest extends ContextTestSupport {

    @Test
    public void testBytesSourceCtr() {
        BytesSource bs = new BytesSource("foo".getBytes());
        assertNotNull(bs.getData());
        assertEquals("BytesSource[foo]", bs.toString());
        assertNull(bs.getSystemId());

        assertNotNull(bs.getInputStream());
        assertNotNull(bs.getReader());
    }

    @Test
    public void testBytesSourceCtrSystemId() {
        BytesSource bs = new BytesSource("foo".getBytes(), "Camel");
        assertNotNull(bs.getData());
        assertEquals("BytesSource[foo]", bs.toString());
        assertEquals("Camel", bs.getSystemId());

        assertNotNull(bs.getInputStream());
        assertNotNull(bs.getReader());
    }
}
