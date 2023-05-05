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
import java.util.Objects;

import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;

import org.apache.camel.Exchange;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JpaOutputTypeTest extends JpaWithOptionsTestSupport {

    private static final String ENDPOINT_URI = "jpa://" + Customer.class.getName();

    private String queryOrFind;

    @Test
    @Query
    @AdditionalQueryParameters("outputType=SelectOne&parameters.seq=% 001")
    public void testSingleCustomerOKQuery() throws Exception {
        final Customer customer = runQueryTest(Customer.class);

        assertNotNull(customer);
    }

    @Test
    @Query("select c from Customer c")
    @AdditionalQueryParameters("outputType=SelectOne")
    public void testTooMuchResults() throws Exception {
        final Exchange result = doRunQueryTest();

        Assertions.assertInstanceOf(NonUniqueResultException.class, getException(result));
    }

    @Test
    @Query
    @AdditionalQueryParameters("outputType=SelectOne&parameters.seq=% xxx")
    public void testNoCustomersQuery() throws Exception {
        final Exchange result = doRunQueryTest();

        Assertions.assertInstanceOf(NoResultException.class, getException(result));
    }

    @Override
    protected String getEndpointUri() {
        return String.format("%s?%s%s",
                ENDPOINT_URI,
                queryOrFind,
                createAdditionalQueryParameters());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);

        // a query or a find is necessary - without the annotation test can't continue
        final AnnotatedElement annotatedElement = context.getElement().get();

        final Find find = annotatedElement.getAnnotation(Find.class);
        final Query query = annotatedElement.getAnnotation(Query.class);

        if ((find == null) == (query == null)) {
            throw new IllegalStateException("Test must be annotated with EITHER Find OR Query");
        }

        if (find != null) {
            queryOrFind = "findEntity=" + true;
        } else { // query != null
            queryOrFind = "query=" + query.value();
        }

    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Find {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Query {
        String value() default "select c from Customer c where c.name like :seq";
    }

    private static Exception getException(final Exchange exchange) {
        return Objects.requireNonNullElse(
                exchange.getException(),
                exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
    }
}
