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

import java.lang.annotation.ElementType;
import java.util.Locale;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.Path;
import javax.validation.Path.Node;
import javax.validation.TraversableResolver;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class BeanValidatorConfigurationTest extends CamelTestSupport {

    @BindToRegistry("myMessageInterpolator")
    private MessageInterpolator messageInterpolator;
    @BindToRegistry("myTraversableResolver")
    private TraversableResolver traversableResolver;
    @BindToRegistry("myConstraintValidatorFactory")
    private ConstraintValidatorFactory constraintValidatorFactory;

    @Override
    @Before
    public void setUp() throws Exception {
        this.messageInterpolator = new MyMessageInterpolator();
        this.traversableResolver = new MyTraversableResolver();
        this.constraintValidatorFactory = new MyConstraintValidatorFactory();

        super.setUp();
    }

    @Test
    public void configureWithDefaults() throws Exception {
        if (isPlatform("aix")) {
            // cannot run on aix
            return;
        }

        BeanValidatorEndpoint endpoint = context.getEndpoint("bean-validator://x", BeanValidatorEndpoint.class);
        assertNull(endpoint.getGroup());
    }

    @Test
    public void configureBeanValidator() throws Exception {
        if (isPlatform("aix")) {
            // cannot run on aix
            return;
        }

        BeanValidatorEndpoint endpoint = context
            .getEndpoint("bean-validator://x" + "?group=org.apache.camel.component.bean.validator.OptionalChecks" + "&messageInterpolator=#myMessageInterpolator"
                         + "&traversableResolver=#myTraversableResolver" + "&constraintValidatorFactory=#myConstraintValidatorFactory", BeanValidatorEndpoint.class);

        assertEquals("org.apache.camel.component.bean.validator.OptionalChecks", endpoint.getGroup());
        assertSame(endpoint.getMessageInterpolator(), this.messageInterpolator);
        assertSame(endpoint.getTraversableResolver(), this.traversableResolver);
        assertSame(endpoint.getConstraintValidatorFactory(), this.constraintValidatorFactory);
    }

    class MyMessageInterpolator implements MessageInterpolator {

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return null;
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return null;
        }
    }

    class MyTraversableResolver implements TraversableResolver {

        @Override
        public boolean isCascadable(Object traversableObject, Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
            return false;
        }

        @Override
        public boolean isReachable(Object traversableObject, Node traversableProperty, Class<?> rootBeanType, Path pathToTraversableObject, ElementType elementType) {
            return false;
        }
    }

    class MyConstraintValidatorFactory implements ConstraintValidatorFactory {

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            return null;
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> arg0) {
            // noop
        }
    }
}
