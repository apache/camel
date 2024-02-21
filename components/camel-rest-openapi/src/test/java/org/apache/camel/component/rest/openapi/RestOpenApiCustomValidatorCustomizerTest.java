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
package org.apache.camel.component.rest.openapi;

import java.net.URI;
import java.util.Set;

import javax.annotation.Nonnull;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.interaction.request.CustomRequestValidator;
import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.openapi.validator.RequestValidationCustomizer;
import org.apache.camel.component.rest.openapi.validator.RestOpenApiOperation;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class RestOpenApiCustomValidatorCustomizerTest extends CamelTestSupport {
    @BindToRegistry("customCustomizer")
    RequestValidationCustomizer customizer = new CustomRequestValidationCustomizer("Test validation error from endpoint");

    @Test
    void customValidatorCustomizerComponentOption() throws Exception {
        Exchange exchange = template.request("direct:componentValidatorCustomizer", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage()
                        .setBody("{\"id\":10,\"name\":\"doggie\",\"photoUrls\":[\"https://test.photos.org/doggie.gif\"]}");
            }
        });
        Exception exception = exchange.getException();
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> validationErrors = validationException.getValidationErrors();
        assertEquals(1, validationErrors.size());
        assertEquals("Test validation error from component", validationErrors.iterator().next());
    }

    @Test
    void customValidatorCustomizerEndpointOption() throws Exception {
        Exchange exchange = template.request("direct:endpointValidatorCustomizer", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage()
                        .setBody("{\"id\":10,\"name\":\"doggie\",\"photoUrls\":[\"https://test.photos.org/doggie.gif\"]}");
            }
        });
        Exception exception = exchange.getException();
        assertInstanceOf(RestOpenApiValidationException.class, exception);

        RestOpenApiValidationException validationException = (RestOpenApiValidationException) exception;
        Set<String> validationErrors = validationException.getValidationErrors();
        assertEquals(1, validationErrors.size());
        assertEquals("Test validation error from endpoint", validationErrors.iterator().next());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName("http");
        component.setConsumes("application/json");
        component.setProduces("application/json");
        component.setSpecificationUri(URI.create("classpath:openapi-v3.json"));
        component.setRequestValidationCustomizer(new CustomRequestValidationCustomizer("Test validation error from component"));
        camelContext.addComponent("petStore", component);

        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:componentValidatorCustomizer")
                        .to("petStore:#addPet?requestValidationEnabled=true");

                from("direct:endpointValidatorCustomizer")
                        .to("petStore:#addPet?requestValidationEnabled=true&requestValidationCustomizer=#customCustomizer");
            }
        };
    }

    static class CustomRequestValidationCustomizer implements RequestValidationCustomizer {
        private final String message;

        CustomRequestValidationCustomizer(String message) {
            this.message = message;
        }

        @Override
        public void customizeOpenApiInteractionValidator(OpenApiInteractionValidator.Builder builder) {
            builder.withCustomRequestValidation(new CustomRequestValidator() {
                @Override
                public ValidationReport validate(@Nonnull Request request, @Nonnull ApiOperation apiOperation) {
                    return ValidationReport.singleton((ValidationReport.Message.create("test.custom", message).build()));
                }
            });
        }

        @Override
        public void customizeSimpleRequestBuilder(
                SimpleRequest.Builder builder, RestOpenApiOperation openApiOperation, Exchange exchange) {
            builder.withHeader("test", exchange.getMessage().getHeader("test", String.class));
        }
    }
}
