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
package org.apache.camel.model.errorhandler;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.spi.Metadata;

/**
 * References to an existing or custom error handler.
 */
@Metadata(label = "configuration,error")
@XmlRootElement(name = "refErrorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class RefErrorHandlerDefinition extends BaseErrorHandlerDefinition {

    public static final String DEFAULT_ERROR_HANDLER_BUILDER = "CamelDefaultErrorHandlerBuilder";

    @XmlAttribute(required = true)
    @Metadata(javaType = "org.apache.camel.ErrorHandlerFactory")
    private String ref;

    public RefErrorHandlerDefinition() {
    }

    public RefErrorHandlerDefinition(String ref) {
        this.ref = ref;
    }

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public ErrorHandlerFactory cloneBuilder() {
        // clone not needed
        return this;
    }

    public String getRef() {
        return ref;
    }

    /**
     * References to an existing or custom error handler.
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    /**
     * References to an existing or custom error handler.
     */
    public RefErrorHandlerDefinition ref(String ref) {
        setRef(ref);
        return this;
    }

}
