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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpaPagingTest extends AbstractJpaMethodSupport {

    private static final String ENDPOINT_URI = "jpa://" + Customer.class.getName() +
                                               "?query=select c from Customer c order by c.name";

    // should be less than 1000 as numbers in entries' names are formatted for sorting with %03d (or change format)
    private static final int ENTRIES_COUNT = 30;

    private static final String ENTRY_SEQ_FORMAT = "%03d";

    // both should be less than ENTRIES_COUNT / 2
    private static final int FIRST_RESULT = 5;
    private static final int MAXIMUM_RESULTS = 10;

    protected String additionalQueryParameters = "";

    @Test
    public void testUnrestrictedQueryReturnsAll() throws Exception {
        final List<Customer> customers = runQueryTest();

        assertEquals(ENTRIES_COUNT, customers.size());
    }

    @Test
    @AdditionalQueryParameters("firstResult=" + FIRST_RESULT)
    public void testFirstResultInUri() throws Exception {
        final List<Customer> customers = runQueryTest();

        assertEquals(ENTRIES_COUNT - FIRST_RESULT, customers.size());
    }

    @Test
    public void testMaxResultsInHeader() throws Exception {
        final List<Customer> customers
                = runQueryTest(exchange -> exchange.getIn().setHeader(JpaConstants.JPA_MAXIMUM_RESULTS, MAXIMUM_RESULTS));

        assertEquals(MAXIMUM_RESULTS, customers.size());
    }

    @Test
    @AdditionalQueryParameters("maximumResults=" + MAXIMUM_RESULTS)
    public void testFirstInHeaderMaxInUri() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_FIRST_RESULT, FIRST_RESULT));

        assertEquals(MAXIMUM_RESULTS, customers.size());
        assertFirstCustomerSequence(customers, FIRST_RESULT);
    }

    @Test
    @AdditionalQueryParameters("maximumResults=" + MAXIMUM_RESULTS)
    public void testMaxHeaderPrevailsOverUri() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_MAXIMUM_RESULTS, MAXIMUM_RESULTS * 2));

        assertEquals(MAXIMUM_RESULTS * 2, customers.size());
    }

    @Test
    @AdditionalQueryParameters("firstResult=" + FIRST_RESULT)
    public void testFirstHeaderPrevailsOverUri() throws Exception {
        final List<Customer> customers = runQueryTest(
                withHeader(JpaConstants.JPA_FIRST_RESULT, FIRST_RESULT * 2));

        assertEquals(ENTRIES_COUNT - (FIRST_RESULT * 2), customers.size());
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
    @AdditionalQueryParameters("firstResult=" + ENTRIES_COUNT)
    public void testFirstResultAfterTheEnd() throws Exception {
        final List<Customer> customers = runQueryTest();

        assertEquals(0, customers.size());
    }

    private static void assertFirstCustomerSequence(final List<Customer> customers, final int firstResult) {
        assertTrue(customers.get(0).getName().endsWith(String.format(ENTRY_SEQ_FORMAT, firstResult)));
    }

    @SuppressWarnings("unchecked")
    protected List<Customer> runQueryTest(final Processor... preRun) throws Exception {
        setUp(getEndpointUri());

        final Exchange result = template.send("direct:start", exchange -> {
            for (Processor processor : preRun) {
                processor.process(exchange);
            }
        });

        return (List<Customer>) result.getMessage().getBody(List.class);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);

        final Optional<AnnotatedElement> element = context.getElement();

        if (element.isPresent()) {
            final AnnotatedElement annotatedElement = element.get();
            final AdditionalQueryParameters annotation = annotatedElement.getAnnotation(AdditionalQueryParameters.class);
            if (annotation != null && !annotation.value().isBlank()) {
                additionalQueryParameters = annotation.value();
            }
        }
    }

    @Override
    protected void setUp(String endpointUri) throws Exception {
        super.setUp(endpointUri);

        createCustomers();
        assertEntitiesInDatabase(ENTRIES_COUNT, Customer.class.getName());
    }

    protected void createCustomers() {
        IntStream.range(0, ENTRIES_COUNT).forEach(idx -> {
            Customer customer = createDefaultCustomer();
            customer.setName(String.format("%s " + ENTRY_SEQ_FORMAT, customer.getName(), idx));
            save(customer);
        });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        final String endpointUri = getEndpointUri();
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(endpointUri);
            }
        };
    }

    protected String getEndpointUri() {
        return ENDPOINT_URI +
               (additionalQueryParameters.isBlank() ? "" : "&" + additionalQueryParameters);
    }

    protected Processor withHeader(final String headerName, final Object headerValue) {
        return exchange -> exchange.getIn().setHeader(headerName, headerValue);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    private @interface AdditionalQueryParameters {
        String value();
    }

}
