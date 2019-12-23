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
package org.apache.camel.component.direct;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.junit.Test;

public class SendToNonExistingDirectEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDirect() throws Exception {
        context.start();

        context.getComponent("direct", DirectComponent.class).setBlock(false);

        try {
            template.sendBody("direct:foo", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            DirectConsumerNotAvailableException cause = assertIsInstanceOf(DirectConsumerNotAvailableException.class, e.getCause());
            assertIsInstanceOf(CamelExchangeException.class, cause); // ensure
                                                                     // backwards
                                                                     // compatibility
            assertNotNull(cause.getExchange());
        }
    }
}
