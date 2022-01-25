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
package org.apache.camel.cdi.xml;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.NoErrorHandlerBuilder;

/**
 * Used to configure the errorHandler type
 */
@XmlType
@XmlEnum(String.class)
@Deprecated
public enum ErrorHandlerType {

    DefaultErrorHandler,
    DeadLetterChannel,
    NoErrorHandler,
    TransactionErrorHandler;

    /**
     * Get the type as class FQN name.
     *
     * @return the class which represents the selected type.
     */
    public String getTypeAsClass() {
        switch (this) {
            case DefaultErrorHandler:
                return DefaultErrorHandlerBuilder.class.getName();
            case DeadLetterChannel:
                return DeadLetterChannelBuilder.class.getName();
            case NoErrorHandler:
                return NoErrorHandlerBuilder.class.getName();
            case TransactionErrorHandler:
                return "org.apache.camel.jta.JtaTransactionErrorHandlerBuilder";
            default:
                throw new IllegalArgumentException("Unknown error handler: " + this);
        }
    }
}
