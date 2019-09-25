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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Validator;

/**
 * Represents a CustomValidator. One of the bean reference (ref) or fully
 * qualified class name (className) of the custom {@link Validator} needs to be
 * specified. {@see ValidatorDefinition} {@see Validator}
 */
@Metadata(label = "validation")
@XmlType(name = "customValidator")
@XmlAccessorType(XmlAccessType.FIELD)
public class CustomValidatorDefinition extends ValidatorDefinition {

    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String className;

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
