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
package org.apache.camel.model.validator;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.spi.Metadata;

/**
 * To configure validators.
 */
@Metadata(label = "validation", title = "Validations")
@XmlRootElement(name = "validators")
@XmlAccessorType(XmlAccessType.FIELD)
public class ValidatorsDefinition {

    @XmlElements({
            @XmlElement(name = "endpointValidator", type = EndpointValidatorDefinition.class),
            @XmlElement(name = "predicateValidator", type = PredicateValidatorDefinition.class),
            @XmlElement(name = "customValidator", type = CustomValidatorDefinition.class) })
    private List<ValidatorDefinition> validators;

    public ValidatorsDefinition() {
    }

    protected ValidatorsDefinition(ValidatorsDefinition source) {
        this.validators = ProcessorDefinitionHelper.deepCopyDefinitions(source.validators);
    }

    /**
     * The configured transformers
     */
    public void setValidators(List<ValidatorDefinition> validators) {
        this.validators = validators;
    }

    public List<ValidatorDefinition> getValidators() {
        return validators;
    }

}
