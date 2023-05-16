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

import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import org.apache.camel.Exchange;
import org.apache.camel.component.jpa.JpaWithOptionsTestSupport.Query;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Query("select c from Customer c where c.name like :seq")
public class JpaOutputTypeTest extends JpaWithOptionsTestSupport {

    @Test
    @AdditionalEndpointParameters("singleResult=true&parameters.seq=% 001")
    public void testSingleCustomerOKQuery() throws Exception {
        final Customer customer = runQueryTest(Customer.class);

        assertNotNull(customer);
    }

    @Test
    @Query("select c from Customer c")
    @AdditionalEndpointParameters("singleResult=true")
    public void testTooMuchResults() throws Exception {
        final Exchange result = doRunQueryTest();

        Assertions.assertInstanceOf(NonUniqueResultException.class, getException(result));
    }

    @Test
    @AdditionalEndpointParameters("singleResult=true&parameters.seq=% xxx")
    public void testNoCustomersQuery() throws Exception {
        final Exchange result = doRunQueryTest();

        Assertions.assertInstanceOf(NoResultException.class, getException(result));
    }

    @Test
    @Find
    @AdditionalEndpointParameters("singleResult=true")
    public void testSingleCustomerOKFind() throws Exception {
        // ids in the db are not known, so query for a known element and use its id.
        Long customerId = validCustomerId(entityManager);

        final Exchange result = template.send("direct:start", withBody(customerId));

        assertNotNull(result.getIn().getBody(Customer.class));
    }

    @Test
    @Find
    @AdditionalEndpointParameters("singleResult=true")
    public void testNoCustomerFind() throws Exception {
        final Exchange result = doRunQueryTest(withBody(Long.MAX_VALUE));

        Assertions.assertInstanceOf(NoResultException.class, getException(result));
    }

    private static Exception getException(final Exchange exchange) {
        final Exception exception = exchange.getException();

        return exception != null ? exception : exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
    }

}
