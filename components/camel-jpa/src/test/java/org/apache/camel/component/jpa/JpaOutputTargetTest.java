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
package org.apache.camel.component.jpa;

import org.apache.camel.Exchange;
import org.apache.camel.component.jpa.JpaWithOptionsTestSupport.Query;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Query
public class JpaOutputTargetTest extends JpaWithOptionsTestSupport {

    private static final String TARGET_NAME = "__target";
    private static final String PROP_MARK = "property:";

    @Test
    @AdditionalEndpointParameters("outputTarget=" + TARGET_NAME)
    public void testQueryToHeader() throws Exception {
        final Exchange result = doRunQueryTest();

        Assertions.assertNotNull(result.getIn().getHeader(TARGET_NAME));
    }

    @Test
    @AdditionalEndpointParameters("outputTarget=" + TARGET_NAME)
    public void testBodyRemainsUnchanged() throws Exception {
        final Object body = new Object();
        final Exchange result = doRunQueryTest(withBody(body));

        Assertions.assertEquals(body, result.getIn().getBody());
    }

    @Test
    @AdditionalEndpointParameters("outputTarget=" + PROP_MARK + TARGET_NAME)
    public void testQueryToProperty() throws Exception {
        final Exchange result = doRunQueryTest();

        Assertions.assertNotNull(result.getProperty(TARGET_NAME));
    }

    @Test
    @Find
    @AdditionalEndpointParameters("outputTarget=" + TARGET_NAME)
    public void testFindToHeader() throws Exception {
        final Long customerId = validCustomerId(entityManager);
        final Exchange result = doRunQueryTest(withBody(customerId));

        Assertions.assertEquals(customerId, result.getIn().getHeader(TARGET_NAME, Customer.class).getId());
    }

    @Test
    @Find
    @AdditionalEndpointParameters("outputTarget=" + TARGET_NAME)
    public void testFindBodyRemainsUnchanged() throws Exception {
        final Object body = new Object();
        final Exchange result = doRunQueryTest(withBody(body));

        Assertions.assertEquals(body, result.getIn().getBody());
    }

    @Test
    @Find
    @AdditionalEndpointParameters("outputTarget=" + PROP_MARK + TARGET_NAME)
    public void testFindToProperty() throws Exception {
        final Long customerId = validCustomerId(entityManager);
        final Exchange result = doRunQueryTest(withBody(customerId));

        Assertions.assertEquals(customerId, result.getProperty(TARGET_NAME, Customer.class).getId());
    }

}
