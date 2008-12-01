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
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;idempotentConsumer/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "idempotentConsumer")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdempotentConsumerType extends ExpressionNode {
    @XmlAttribute
    private String messageIdRepositoryRef;
    @XmlTransient
    private IdempotentRepository idempotentRepository;

    public IdempotentConsumerType() {
    }

    public IdempotentConsumerType(Expression messageIdExpression, IdempotentRepository idempotentRepository) {
        super(messageIdExpression);
        this.idempotentRepository = idempotentRepository;
    }

    @Override
    public String toString() {
        return "IdempotentConsumer[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "idempotentConsumer";
    }

    public String getMessageIdRepositoryRef() {
        return messageIdRepositoryRef;
    }

    public void setMessageIdRepositoryRef(String messageIdRepositoryRef) {
        this.messageIdRepositoryRef = messageIdRepositoryRef;
    }

    public IdempotentRepository getMessageIdRepository() {
        return idempotentRepository;
    }

    public void setMessageIdRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        IdempotentRepository idempotentRepository = resolveMessageIdRepository(routeContext);
        return new IdempotentConsumer(getExpression().createExpression(routeContext), idempotentRepository,
                                      childProcessor);
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use
     *
     * @param routeContext  route context
     * @return the repository
     */
    protected IdempotentRepository resolveMessageIdRepository(RouteContext routeContext) {
        if (idempotentRepository == null) {
            idempotentRepository = routeContext.lookup(messageIdRepositoryRef, IdempotentRepository.class);
        }
        return idempotentRepository;
    }
}
