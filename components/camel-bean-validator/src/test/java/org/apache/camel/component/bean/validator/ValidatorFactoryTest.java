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

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.bootstrap.GenericBootstrap;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class ValidatorFactoryTest extends CamelTestSupport {

    @BindToRegistry("myValidatorFactory")
    private ValidatorFactory validatorFactory;

    @Override
    @Before
    public void setUp() throws Exception {
        GenericBootstrap bootstrap = Validation.byDefaultProvider();
        bootstrap.providerResolver(new HibernateValidationProviderResolver());

        this.validatorFactory = bootstrap.configure().buildValidatorFactory();

        super.setUp();
    }

    @Test
    public void configureValidatorFactoryFromRegistry() throws Exception {
        if (isPlatform("aix")) {
            // cannot run on aix
            return;
        }

        BeanValidatorEndpoint endpoint = context.getEndpoint("bean-validator:dummy?validatorFactory=#myValidatorFactory", BeanValidatorEndpoint.class);
        BeanValidatorProducer producer = (BeanValidatorProducer)endpoint.createProducer();

        assertSame(endpoint.getValidatorFactory(), this.validatorFactory);
        assertSame(producer.getValidatorFactory(), this.validatorFactory);
    }

    @Test
    public void configureValidatorFactory() throws Exception {
        if (isPlatform("aix")) {
            // cannot run on aix
            return;
        }

        BeanValidatorEndpoint endpoint = context.getEndpoint("bean-validator:dummy", BeanValidatorEndpoint.class);
        BeanValidatorProducer producer = (BeanValidatorProducer)endpoint.createProducer();

        assertNull(endpoint.getValidatorFactory());
        assertNotSame(endpoint.getValidatorFactory(), this.validatorFactory);
        assertNotNull(producer.getValidatorFactory());
        assertNotSame(producer.getValidatorFactory(), this.validatorFactory);
    }
}
