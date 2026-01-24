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
import org.apache.camel.FailedToCreateRouteException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SpringTryCatchMisconfiguredTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        Exception e1 = assertThrows(Exception.class, () -> {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringTryCatchMisconfiguredTest.xml");
        });
        FailedToCreateRouteException ftce = assertIsInstanceOf(FailedToCreateRouteException.class, e1);
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, ftce.getCause());
        assertEquals(
                "This doCatch should have a doTry as its parent on DoCatch[ [class java.io.IOException] -> [to[mock:fail]]]",
                iae.getMessage());

        Exception e2 = assertThrows(Exception.class, () -> {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringTryCatchMisconfiguredFinallyTest.xml");
        });
        FailedToCreateRouteException ftcre = assertIsInstanceOf(FailedToCreateRouteException.class, e2);
        IllegalArgumentException iae2 = assertIsInstanceOf(IllegalArgumentException.class, ftcre.getCause());
        assertEquals("This doFinally should have a doTry as its parent on DoFinally[[to[mock:finally]]]", iae2.getMessage());

        // return a working context instead, to let this test pass
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/convertBody.xml");
    }

    @Test
    public void testTryCatchMisconfigured() {
        // noop
    }

}
