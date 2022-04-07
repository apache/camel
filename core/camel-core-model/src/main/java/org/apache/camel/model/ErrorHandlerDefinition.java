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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.JtaTransactionErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.SpringTransactionErrorHandlerDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Camel error handling.
 */
@Metadata(label = "configuration,error")
@XmlRootElement(name = "errorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class ErrorHandlerDefinition extends IdentifiedType {

    @XmlElements({
            @XmlElement(name = "deadLetterChannel", type = DeadLetterChannelDefinition.class),
            @XmlElement(name = "defaultErrorHandler", type = DefaultErrorHandlerDefinition.class),
            @XmlElement(name = "noErrorHandler", type = NoErrorHandlerDefinition.class),
            @XmlElement(name = "jtaTransactionErrorHandler", type = JtaTransactionErrorHandlerDefinition.class),
            @XmlElement(name = "springTransactionErrorHandler", type = SpringTransactionErrorHandlerDefinition.class) })
    private ErrorHandlerFactory errorHandlerType;

    public ErrorHandlerFactory getErrorHandlerType() {
        return errorHandlerType;
    }

    public void setErrorHandlerType(ErrorHandlerFactory errorHandlerType) {
        this.errorHandlerType = errorHandlerType;
    }

    @Override
    public String toString() {
        return "ErrorHandler[" + description() + "]";
    }

    protected String description() {
        return errorHandlerType != null ? errorHandlerType.toString() : "";
    }

}
