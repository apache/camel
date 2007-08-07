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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.idempotent.MessageIdRepository;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "idempotentConsumer")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdempotentConsumerType extends ExpressionNode {
    @XmlAttribute
    private String messageIdRepositoryRef;
    @XmlTransient
    private MessageIdRepository messageIdRepository;

    public IdempotentConsumerType() {
    }

    public IdempotentConsumerType(Expression messageIdExpression, MessageIdRepository messageIdRepository) {
        super(messageIdExpression);
        this.messageIdRepository = messageIdRepository;
    }

    @Override
    public String toString() {
        return "IdempotentConsumer[ " + getExpression() + " -> " + getOutputs() + "]";
    }

    public String getMessageIdRepositoryRef() {
        return messageIdRepositoryRef;
    }

    public void setMessageIdRepositoryRef(String messageIdRepositoryRef) {
        this.messageIdRepositoryRef = messageIdRepositoryRef;
    }

    public MessageIdRepository getMessageIdRepository() {
        return messageIdRepository;
    }

    public void setMessageIdRepository(MessageIdRepository messageIdRepository) {
        this.messageIdRepository = messageIdRepository;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        MessageIdRepository messageIdRepository = resolveMessageIdRepository(routeContext);
        return new IdempotentConsumer(getExpression().createExpression(routeContext), messageIdRepository,
                                      childProcessor);
    }

    public MessageIdRepository resolveMessageIdRepository(RouteContext routeContext) {
        if (messageIdRepository == null) {
            messageIdRepository = routeContext.lookup(messageIdRepositoryRef, MessageIdRepository.class);
        }
        return messageIdRepository;
    }
}
