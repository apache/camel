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

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class JpaWithOptionsTestSupport extends AbstractJpaMethodSupport {

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

    @SuppressWarnings(value = "unchecked")
    protected List<Customer> runQueryTest(final Processor... preRun) throws Exception {
        setUp(getEndpointUri());

        final Exchange result = template.send("direct:start", exchange -> {
            for (Processor processor : preRun) {
                processor.process(exchange);
            }
        });
        return (List<Customer>) result.getMessage().getBody(List.class);
    }

    protected Processor withHeader(final String headerName, final Object headerValue) {
        return exchange -> exchange.getIn().setHeader(headerName, headerValue);
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

}
