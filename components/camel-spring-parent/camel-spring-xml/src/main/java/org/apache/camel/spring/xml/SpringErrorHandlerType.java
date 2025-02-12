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
package org.apache.camel.spring.xml;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.builder.LegacyDeadLetterChannelBuilder;
import org.apache.camel.builder.LegacyDefaultErrorHandlerBuilder;
import org.apache.camel.builder.LegacyNoErrorHandlerBuilder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spring.spi.LegacyTransactionErrorHandlerBuilder;

/**
 * Used to configure the error handler type
 */
@Metadata(label = "spring,configuration,error")
@XmlType
@XmlEnum
public enum SpringErrorHandlerType {

    DefaultErrorHandler,
    DeadLetterChannel,
    NoErrorHandler,
    TransactionErrorHandler;

    /**
     * Get the type as class.
     *
     * @return the class which represents the selected type.
     */
    public Class<?> getTypeAsClass() {
        switch (this) {
            case DefaultErrorHandler:
                return LegacyDefaultErrorHandlerBuilder.class;
            case DeadLetterChannel:
                return LegacyDeadLetterChannelBuilder.class;
            case NoErrorHandler:
                return LegacyNoErrorHandlerBuilder.class;
            case TransactionErrorHandler:
                return LegacyTransactionErrorHandlerBuilder.class;
            default:
                throw new IllegalArgumentException("Unknown error handler: " + this);
        }
    }

}
