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
package org.apache.camel.test.main.junit5.legacy;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.main.junit5.CamelMainTestSupport;
import org.apache.camel.test.main.junit5.common.MyMainClass;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test class ensuring that a new camel context is created for the entire test class.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestInstancePerClassTest extends CamelMainTestSupport {

    @Override
    protected Class<?> getMainClass() {
        return MyMainClass.class;
    }

    @Order(1)
    @Test
    void shouldBeLaunchedFirst() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:bean", MockEndpoint.class);
        mock.expectedBodiesReceived(1);
        int result = template.requestBody("direct:bean", null, Integer.class);
        mock.assertIsSatisfied();
        assertEquals(1, result);
    }

    @Order(2)
    @Test
    void shouldBeLaunchedSecondWithDifferentResult() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:bean", MockEndpoint.class);
        mock.expectedBodiesReceived(2);
        int result = template.requestBody("direct:bean", null, Integer.class);
        mock.assertIsSatisfied();
        assertEquals(2, result);
    }
}
