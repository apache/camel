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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTryCatchMisconfiguredTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        // Do NOT call super.setUp() — this test validates that context creation fails
    }

    @Test
    void testTryCatchMisconfigured() throws Exception {
        Exception e1 = assertThrows(Exception.class, () -> {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringTryCatchMisconfiguredTest.xml");
        });
        FailedToCreateRouteException ftce = assertIsInstanceOf(FailedToCreateRouteException.class, e1);
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, ftce.getCause());
        // the doCatch is not inside the doTry, so the doTry has no doCatch or doFinally
        assertTrue(iae.getMessage().startsWith("doTry must have one or more doCatch or doFinally blocks"),
                iae.getMessage());

        Exception e2 = assertThrows(Exception.class, () -> {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringTryCatchMisconfiguredFinallyTest.xml");
        });
        FailedToCreateRouteException ftcre = assertIsInstanceOf(FailedToCreateRouteException.class, e2);
        IllegalArgumentException iae2 = assertIsInstanceOf(IllegalArgumentException.class, ftcre.getCause());
        assertTrue(iae2.getMessage().startsWith("doTry must have one or more doCatch or doFinally blocks"),
                iae2.getMessage());
    }

}
