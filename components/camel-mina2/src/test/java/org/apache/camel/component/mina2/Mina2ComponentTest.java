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
package org.apache.camel.component.mina2;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * For testing various minor holes that hasn't been covered by other unit tests.
 *
 * @version 
 */
public class Mina2ComponentTest extends CamelTestSupport {

    @Test
    public void testUnknownProtocol() {
        try {
            template.sendBody("mina2:xxx://localhost:8080", "mina2:xxx://localhost:8080");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue("Should be an IAE exception", e.getCause() instanceof IllegalArgumentException);
            assertEquals("Unrecognised MINA protocol: xxx for uri: mina2://xxx://localhost:8080", e.getCause().getMessage());
        }
    }

    @Test
    public void testMistypedProtocol() {
        try {
            // the protocol is mistyped as a colon is missing after tcp
            template.sendBody("mina2:tcp//localhost:8080", "mina2:tcp//localhost:8080");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue("Should be an IAE exception", e.getCause() instanceof IllegalArgumentException);
            assertEquals("Unrecognised MINA protocol: null for uri: mina2://tcp//localhost:8080", e.getCause().getMessage());
        }
    }
}
