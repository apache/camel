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
package org.apache.camel.spring;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.LoggingLevel;
import org.apache.camel.model.IdentifiedType;

/**
 * The &lt;errorHandler&gt; tag element.
 *
 * @version 
 */
@XmlRootElement(name = "errorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class ErrorHandlerDefinition extends IdentifiedType {
    @XmlAttribute
    private ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;
    @XmlAttribute
    private String deadLetterUri;
    @XmlAttribute
    private String deadLetterHandleNewException;
    @XmlAttribute
    private LoggingLevel level;
    @XmlAttribute
    private LoggingLevel rollbackLoggingLevel;
    @XmlAttribute
    private String logName;
    @XmlAttribute
    private Boolean useOriginalMessage;
    @XmlAttribute
    private String transactionTemplateRef;
    @XmlAttribute
    private String transactionManagerRef;
    @XmlAttribute
    private String onRedeliveryRef;
    @XmlAttribute
    private String onPrepareFailureRef;
    @XmlAttribute
    private String retryWhileRef;
    @XmlAttribute
    private String redeliveryPolicyRef;
    @XmlAttribute
    private String executorServiceRef;
    @XmlElement
    private CamelRedeliveryPolicyFactoryBean redeliveryPolicy;

}
