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
import org.apache.camel.builder.ExpressionClause;
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
public class IdempotentConsumerDefinition extends ExpressionNode {
    @XmlAttribute
    private String messageIdRepositoryRef;
    @XmlAttribute
    private Boolean eager;
    @XmlTransient
    private IdempotentRepository<?> idempotentRepository;

    public IdempotentConsumerDefinition() {
    }

    public IdempotentConsumerDefinition(Expression messageIdExpression, IdempotentRepository<?> idempotentRepository) {
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
    
    // Fluent API
    //-------------------------------------------------------------------------
    /**
     * Set the expression that IdempotentConsumerType will use
     * @return the builder
     */
    public ExpressionClause<IdempotentConsumerDefinition> expression() {
        return ExpressionClause.createAndSetExpression(this);
    }
    
    /**
     * Sets the reference name of the message id repository
     *
     * @param messageIdRepositoryRef  the reference name of message id repository
     * @return builder
     */
    public IdempotentConsumerDefinition messageIdRepositoryRef(String messageIdRepositoryRef) {
        setMessageIdRepositoryRef(messageIdRepositoryRef);
        return this;
    }
    
    /**
     * Sets the the message id repository for the IdempotentConsumerType
     *
     * @param idempotentRepository  the repository instance of idempotent
     * @return builder
     */
    public IdempotentConsumerDefinition messageIdRepository(IdempotentRepository<?> idempotentRepository) {
        setMessageIdRepository(idempotentRepository);
        return this;
    }

    /**
     * Sets whether to eagerly add the key to the idempotent repository or wait until the exchange
     * is complete. Eager is default enabled.
     *
     * @param eager  <tt>true</tt> to add the key before processing, <tt>false</tt> to wait until
     * the exchange is complete.
     * @return builder
     */
    public IdempotentConsumerDefinition eager(boolean eager) {
        setEager(eager);
        return this;
    }

    public String getMessageIdRepositoryRef() {
        return messageIdRepositoryRef;
    }

    public void setMessageIdRepositoryRef(String messageIdRepositoryRef) {
        this.messageIdRepositoryRef = messageIdRepositoryRef;
    }

    public IdempotentRepository<?> getMessageIdRepository() {
        return idempotentRepository;
    }

    public void setMessageIdRepository(IdempotentRepository<?> idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public Boolean isEager() {
        return eager;
    }

    public void setEager(Boolean eager) {
        this.eager = eager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        IdempotentRepository<String> idempotentRepository =
            (IdempotentRepository<String>) resolveMessageIdRepository(routeContext);

        Expression expression = getExpression().createExpression(routeContext);
        // should be eager by default
        boolean isEager = isEager() != null ? isEager() : true;
        return new IdempotentConsumer(expression, idempotentRepository, isEager, childProcessor);
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use
     *
     * @param routeContext  route context
     * @return the repository
     */
    protected IdempotentRepository<?> resolveMessageIdRepository(RouteContext routeContext) {
        if (idempotentRepository == null) {
            idempotentRepository = routeContext.lookup(messageIdRepositoryRef, IdempotentRepository.class);
        }
        return idempotentRepository;
    }
}
