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
package org.apache.camel.component.bean.validator;

import java.util.List;

import javax.validation.ValidationProviderResolver;
import javax.validation.spi.ValidationProvider;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.hibernate.validator.HibernateValidator;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CustomValidationProviderResolverTest extends CamelTestSupport {

    // Routing fixtures

    @BindToRegistry("myValidationProviderResolver")
    ValidationProviderResolver validationProviderResolver = mock(ValidationProviderResolver.class);

    @Override
    protected void doPreSetup() throws Exception {
        List<ValidationProvider<?>> validationProviders = asList(new HibernateValidator());
        given(validationProviderResolver.getValidationProviders()).willReturn(validationProviders);
        super.doPreSetup();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").to("bean-validator://ValidationProviderResolverTest?validationProviderResolver=#myValidationProviderResolver");
            }
        };
    }

    // Tests

    @Test
    public void shouldResolveCustomValidationProviderResolver() {
        verify(validationProviderResolver, atLeastOnce()).getValidationProviders();
    }

}
