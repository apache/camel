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

import java.util.Map;

import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.ValidationProviderResolver;
import javax.validation.ValidatorFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Bean Validator Component for validating Java beans against reference implementation of JSR 303 Validator (Hibernate
 * Validator).
 */
@Component("bean-validator")
public class BeanValidatorComponent extends DefaultComponent {

    @Metadata
    private boolean ignoreXmlConfiguration;
    @Metadata(label = "advanced")
    private ValidationProviderResolver validationProviderResolver;
    @Metadata(label = "advanced")
    private MessageInterpolator messageInterpolator;
    @Metadata(label = "advanced")
    private TraversableResolver traversableResolver;
    @Metadata(label = "advanced")
    private ConstraintValidatorFactory constraintValidatorFactory;
    @Metadata(label = "advanced")
    private ValidatorFactory validatorFactory;

    public BeanValidatorComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanValidatorEndpoint endpoint = new BeanValidatorEndpoint(uri, this);

        endpoint.setLabel(remaining);
        endpoint.setIgnoreXmlConfiguration(ignoreXmlConfiguration);
        endpoint.setValidationProviderResolver(validationProviderResolver);
        endpoint.setMessageInterpolator(messageInterpolator);
        endpoint.setTraversableResolver(traversableResolver);
        endpoint.setConstraintValidatorFactory(constraintValidatorFactory);
        endpoint.setValidatorFactory(validatorFactory);

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public boolean isIgnoreXmlConfiguration() {
        return ignoreXmlConfiguration;
    }

    /**
     * Whether to ignore data from the META-INF/validation.xml file.
     */
    public void setIgnoreXmlConfiguration(boolean ignoreXmlConfiguration) {
        this.ignoreXmlConfiguration = ignoreXmlConfiguration;
    }

    public ValidationProviderResolver getValidationProviderResolver() {
        return validationProviderResolver;
    }

    /**
     * To use a a custom {@link ValidationProviderResolver}
     */
    public void setValidationProviderResolver(ValidationProviderResolver validationProviderResolver) {
        this.validationProviderResolver = validationProviderResolver;
    }

    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    /**
     * To use a custom {@link MessageInterpolator}
     */
    public void setMessageInterpolator(MessageInterpolator messageInterpolator) {
        this.messageInterpolator = messageInterpolator;
    }

    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    /**
     * To use a custom {@link TraversableResolver}
     */
    public void setTraversableResolver(TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
    }

    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
    }

    /**
     * To use a custom {@link ConstraintValidatorFactory}
     */
    public void setConstraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
        this.constraintValidatorFactory = constraintValidatorFactory;
    }

}
