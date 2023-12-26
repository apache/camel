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

import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationProviderResolver;
import jakarta.validation.ValidatorFactory;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.bean.validator.ValidatorFactories.buildValidatorFactory;

/**
 * Validate the message body using the Java Bean Validation API.
 *
 * Camel uses the reference implementation, which is Hibernate Validator.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "bean-validator", title = "Bean Validator", syntax = "bean-validator:label",
             remote = false, producerOnly = true, category = { Category.VALIDATION })
public class BeanValidatorEndpoint extends DefaultEndpoint {

    @UriPath(description = "Where label is an arbitrary text value describing the endpoint")
    @Metadata(required = true)
    private String label;
    @UriParam(defaultValue = "jakarta.validation.groups.Default")
    private String group;
    @UriParam
    private boolean ignoreXmlConfiguration;
    @UriParam(label = "advanced")
    private ValidationProviderResolver validationProviderResolver;
    @UriParam(label = "advanced")
    private MessageInterpolator messageInterpolator;
    @UriParam(label = "advanced")
    private TraversableResolver traversableResolver;
    @UriParam(label = "advanced")
    private ConstraintValidatorFactory constraintValidatorFactory;
    @UriParam(label = "advanced")
    private ValidatorFactory validatorFactory;

    public BeanValidatorEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        BeanValidatorProducer producer = new BeanValidatorProducer(this);
        if (group != null) {
            producer.setGroup(getCamelContext().getClassResolver().resolveMandatoryClass(group));
        }

        ValidatorFactory validatorFactory = this.validatorFactory;
        if (validatorFactory == null) {
            validatorFactory = buildValidatorFactory(getCamelContext(), isIgnoreXmlConfiguration(),
                    validationProviderResolver, messageInterpolator, traversableResolver, constraintValidatorFactory);
        }

        producer.setValidatorFactory(validatorFactory);
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported");
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getGroup() {
        return group;
    }

    /**
     * To use a custom validation group
     */
    public void setGroup(String group) {
        this.group = group;
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

    /**
     * To use a custom {@link ValidatorFactory}
     */
    public void setValidatorFactory(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
    }

    public ValidatorFactory getValidatorFactory() {
        return validatorFactory;
    }
}
