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
package org.apache.camel.model.validator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Validator;

/**
 * Represents a CustomValidator. One of the bean reference (ref) or fully qualified class name (className)
 * of the custom {@link Validator} needs to be specified.
 * 
 * {@see ValidatorDefinition}
 * {@see Validator}
 */
@Metadata(label = "validation")
@XmlType(name = "customValidator")
@XmlAccessorType(XmlAccessType.FIELD)
public class CustomValidatorDefinition extends ValidatorDefinition {

    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String className;

    @Override
    protected Validator doCreateValidator(CamelContext context) throws Exception {
        if (ref == null && className == null) {
            throw new IllegalArgumentException("'ref' or 'type' must be specified for customValidator");
        }
        Validator validator;
        if (ref != null) {
            validator = context.getRegistry().lookupByNameAndType(ref, Validator.class);
            if (validator == null) {
                throw new IllegalArgumentException("Cannot find validator with ref:" + ref);
            }
            if (validator.getType() != null) {
                throw new IllegalArgumentException(String.format("Validator '%s' is already in use. Please check if duplicate validator exists.", ref));
            }
        } else {
            Class<Validator> validatorClass = context.getClassResolver().resolveMandatoryClass(className, Validator.class);
            if (validatorClass == null) {
                throw new IllegalArgumentException("Cannot find validator class: " + className);
            }
            validator = context.getInjector().newInstance(validatorClass);

        }
        validator.setCamelContext(context);
        return validator.setType(getType());
    }

    public String getRef() {
        return ref;
    }

    /**
     * Set a bean reference of the {@link Validator}
     *
     * @param ref the bean reference of the Transformer
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getClassName() {
        return className;
    }

    /**
     * Set a class name of the {@link Validator}
     *
     * @param className the class name of the Validator
     */
    public void setClassName(String className) {
        this.className = className;
    }

}

