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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class JpaWithOptionsTestSupport extends AbstractJpaMethodSupport {

    // should be less than 1000 as numbers in entries' names are formatted for sorting with %03d (or change format)
    static final int ENTRIES_COUNT = 30;
    static final String ENTRY_SEQ_FORMAT = "%03d";

    private String additionalQueryParameters = "";

    protected String createAdditionalQueryParameters() {
        return additionalQueryParameters.isBlank() ? "" : "&" + additionalQueryParameters;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        final String endpointUri = getEndpointUri();
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(endpointUri);
            }
        };
    }

    protected Exchange doRunQueryTest(final Processor... preRun) throws Exception {
        setUp(getEndpointUri());

        return template.send("direct:start", exchange -> {
            for (Processor processor : preRun) {
                processor.process(exchange);
            }
        });

    }

    protected <E> E runQueryTest(Class<E> type, final Processor... preRun) throws Exception {
        final Exchange result = doRunQueryTest(preRun);

        Assertions.assertNull(result.getException());
        Assertions.assertNull(result.getProperty(Exchange.EXCEPTION_CAUGHT));

        return result.getMessage().getBody(type);

    }

    @SuppressWarnings(value = "unchecked")
    protected List<Customer> runQueryTest(final Processor... preRun) throws Exception {
        return (List<Customer>) runQueryTest(List.class, preRun);
    }

    protected Processor withHeader(final String headerName, final Object headerValue) {
        return exchange -> exchange.getIn().setHeader(headerName, headerValue);
    }

    protected Processor withBody(final Object body) {
        return exchange -> exchange.getIn().setBody(body);
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

    protected abstract String getEndpointUri();

    protected void createCustomers() {
        IntStream.range(0, ENTRIES_COUNT).forEach(idx -> {
            Customer customer = createDefaultCustomer();
            customer.setName(String.format("%s " + ENTRY_SEQ_FORMAT, customer.getName(), idx));
            save(customer);
        });
    }

    @Override
    protected void setUp(String endpointUri) throws Exception {
        super.setUp(endpointUri);
        createCustomers();
        assertEntitiesInDatabase(ENTRIES_COUNT, Customer.class.getName());
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Find {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Query {
        String value() default "select c from Customer c where c.name like :seq";
    }

}
