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
package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;

/**
 * To test timeout.
 *
 * @version $Revision$
 */
public class MinaConfigurationTest extends ContextTestSupport {

    private static final int PORT = 6337;

    public void testTimeoutInvalidParameter() throws Exception {
        // invalid timeout parameter that can not be converted to a number
        try {
            context.getEndpoint("mina:tcp://localhost:" + PORT + "?textline=true&sync=true&timeout=XXX");
            fail("Should have thrown a ResolveEndpointFailedException due to invalid timeout parameter");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("The timeout parameter is not a number: XXX", e.getCause().getMessage());
        }
    }
}