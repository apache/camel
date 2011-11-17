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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.ProxyInstantiationException;

/**
 * @version 
 */
public class ProxyInstantiationExceptionTest extends ContextTestSupport {

    public void testProxyException() {
        Endpoint endpoint = context.getEndpoint("mock:foo");
        ProxyInstantiationException e = new ProxyInstantiationException(CamelContext.class, endpoint, new IllegalArgumentException("Damn"));

        assertNotNull(e);
        assertNotNull(e.getMessage());
        assertSame(endpoint, e.getEndpoint());
        assertEquals(CamelContext.class, e.getType());
        assertNotNull(e.getCause());
        assertEquals("Damn", e.getCause().getMessage());
    }

}
