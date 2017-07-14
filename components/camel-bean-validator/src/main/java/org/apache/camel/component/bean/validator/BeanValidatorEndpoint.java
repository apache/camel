/**
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

import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.ValidationProviderResolver;
import javax.validation.ValidatorFactory;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.PlatformHelper;

import static org.apache.camel.component.bean.validator.ValidatorFactories.buildValidatorFactory;

/**
 * The Validator component performs bean validation of the message body using the Java Bean Validation API.
 *
 * Camel uses the reference implementation, which is Hibernate Validator.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "bean-validator", title = "Bean Validator", syntax = "bean-validator:label", producerOnly = true, label = "validation")
public class BeanValidatorEndpoint extends DefaultEndpoint {

    @UriPath(description = "Where label is an arbitrary text value describing the endpoint") @Metadata(required = "true")
    private String label;
    @UriParam(defaultValue = "javax.validation.groups.Default")
    private String group;
    @UriParam
    private ValidationProviderResolver validationProviderResolver;
    @UriParam
    private MessageInterpolator messageInterpolator;
    @UriParam
    private TraversableResolver traversableResolver;
    @UriParam
    private ConstraintValidatorFactory constraintValidatorFactory; 

    public BeanValidatorEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        BeanValidatorProducer producer = new BeanValidatorProducer(this);
        if (group != null) {
            producer.setGroup(getCamelContext().getClassResolver().resolveMandatoryClass(group));
        }
        ValidatorFactory validatorFactory = buildValidatorFactory(isOsgiContext(),
                validationProviderResolver, messageInterpolator, traversableResolver, constraintValidatorFactory);
        producer.setValidatorFactory(validatorFactory);
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * Recognizes if component is executed in the OSGi environment.
     *
     * @return true if component is executed in the OSGi environment. False otherwise.
     */
    protected boolean isOsgiContext() {
        return PlatformHelper.isOsgiContext(getCamelContext());
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
