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
package org.apache.camel.spring.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringRouteTopLevelMisconfiguredTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        RuntimeCamelException e1 = assertThrows(RuntimeCamelException.class, () -> {
            createSpringCamelContext(this,
                    "org/apache/camel/spring/processor/SpringRouteTopLevelOnExceptionMisconfiguredTest.xml");
        });
        IllegalArgumentException iae1 = assertIsInstanceOf(IllegalArgumentException.class, e1.getCause());
        assertTrue(iae1.getMessage().startsWith("The output must be added as top-level on the route."));

        RuntimeCamelException e2 = assertThrows(RuntimeCamelException.class, () -> {
            createSpringCamelContext(this,
                    "org/apache/camel/spring/processor/SpringRouteTopLevelOnCompletionMisconfiguredTest.xml");
        });
        IllegalArgumentException iae2 = assertIsInstanceOf(IllegalArgumentException.class, e2.getCause());
        assertTrue(iae2.getMessage().startsWith("The output must be added as top-level on the route."));

        RuntimeCamelException e3 = assertThrows(RuntimeCamelException.class, () -> {
            createSpringCamelContext(this,
                    "org/apache/camel/spring/processor/SpringRouteTopLevelTransactedMisconfiguredTest.xml");
        });
        IllegalArgumentException iae3 = assertIsInstanceOf(IllegalArgumentException.class, e3.getCause());
        assertTrue(iae3.getMessage().startsWith("The output must be added as top-level on the route."));

        // return a working context instead, to let this test pass
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/convertBody.xml");
    }

    @Test
    public void testMisconfigured() {
        // noop
    }

}
