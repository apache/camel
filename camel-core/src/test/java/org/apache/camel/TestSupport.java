/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A bunch of useful testing methods
 * 
 * @version $Revision$
 */
public abstract class TestSupport extends TestCase {

    protected transient Log log = LogFactory.getLog(getClass());

    protected <T> T assertIsInstanceOf(Class<T> expectedType, Object value) {
        assertNotNull("Expected an instance of type: " + expectedType.getName() + " but was null", value);
        assertTrue("object should be a " + expectedType.getName() + " but was: " + value + " with type: " + value.getClass().getName(),
                expectedType.isInstance(value));
        return expectedType.cast(value);
    }

    protected void assertEndpointUri(Endpoint<Exchange> endpoint, String uri) {
        assertEquals("Endoint uri for: " + endpoint, uri, endpoint.getEndpointUri());
    }
}
