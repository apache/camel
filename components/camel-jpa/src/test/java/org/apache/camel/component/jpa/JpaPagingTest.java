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

import java.util.List;

import org.apache.camel.component.jpa.JpaWithOptionsTestSupport.Query;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Query("select c from Customer c order by c.name")
public class JpaPagingTest extends JpaWithOptionsTestSupport {

    // both should be less than JpaWithOptionsTestSupport.ENTRIES_COUNT / 2
    private static final int FIRST_RESULT = 5;
    private static final int MAXIMUM_RESULTS = 10;

    @Test
    public void testUnrestrictedQueryReturnsAll() throws Exception {
        final List<Customer> customers = runQueryTest();

        assertEquals(JpaWithOptionsTestSupport.ENTRIES_COUNT, customers.size());
    }

    @Test
    @AdditionalEndpointParameters("firstResult=" + FIRST_RESULT)
    public void testFirstResultInUri() throws Exception {
        final List<Customer> customers = runQueryTest();

        assertEquals(JpaWithOptionsTestSupport.ENTRIES_COUNT - FIRST_RESULT, customers.size());
    }

    @Test
    public void testMaxResultsInHeader() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_MAXIMUM_RESULTS, MAXIMUM_RESULTS));

        assertEquals(MAXIMUM_RESULTS, customers.size());
    }

    @Test
    @AdditionalEndpointParameters("maximumResults=" + MAXIMUM_RESULTS)
    public void testFirstInHeaderMaxInUri() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_FIRST_RESULT, FIRST_RESULT));

        assertEquals(MAXIMUM_RESULTS, customers.size());
        assertFirstCustomerSequence(customers, FIRST_RESULT);
    }

    @Test
    @AdditionalEndpointParameters("maximumResults=" + MAXIMUM_RESULTS)
    public void testMaxHeaderPrevailsOverUri() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_MAXIMUM_RESULTS, MAXIMUM_RESULTS * 2));

        assertEquals(MAXIMUM_RESULTS * 2, customers.size());
    }

    @Test
    @AdditionalEndpointParameters("firstResult=" + FIRST_RESULT)
    public void testFirstHeaderPrevailsOverUri() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_FIRST_RESULT, FIRST_RESULT * 2));

        assertEquals(JpaWithOptionsTestSupport.ENTRIES_COUNT - (FIRST_RESULT * 2), customers.size());
        assertFirstCustomerSequence(customers, FIRST_RESULT * 2);
    }

    @Test
    public void testBothInHeader() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_FIRST_RESULT, FIRST_RESULT),
                withHeader(JpaConstants.JPA_MAXIMUM_RESULTS, MAXIMUM_RESULTS));

        assertEquals(MAXIMUM_RESULTS, customers.size());
        assertFirstCustomerSequence(customers, FIRST_RESULT);
    }

    @Test
    @AdditionalEndpointParameters("firstResult=" + JpaWithOptionsTestSupport.ENTRIES_COUNT)
    public void testFirstResultAfterTheEnd() throws Exception {
        final List<Customer> customers = runQueryTest();

        assertEquals(0, customers.size());
    }

    private static void assertFirstCustomerSequence(final List<Customer> customers, final int firstResult) {
        assertTrue(customers.get(0).getName().endsWith(
                String.format(JpaWithOptionsTestSupport.ENTRY_SEQ_FORMAT, firstResult)));
    }

}
