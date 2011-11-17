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
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;idempotentConsumer/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "idempotentConsumer")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdempotentConsumerDefinition extends ExpressionNode {
    @XmlAttribute
    private String messageIdRepositoryRef;
    @XmlAttribute
    private Boolean eager;
    @XmlAttribute
    private Boolean skipDuplicate;
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
     * Sets the the message id repository for the idempotent consumer
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

    /**
     * Sets whether to skip duplicates or not.
     * <p/>
     * The default behavior is to skip duplicates.
     * <p/>
     * A duplicate message would have the Exchange property {@link org.apache.camel.Exchange#DUPLICATE_MESSAGE} set
     * to a {@link Boolean#TRUE} value. A none duplicate message will not have this property set.
     *
     * @param skipDuplicate  <tt>true</tt> to skip duplicates, <tt>false</tt> to allow duplicates.
     * @return builder
     */
    public IdempotentConsumerDefinition skipDuplicate(boolean skipDuplicate) {
        setSkipDuplicate(skipDuplicate);
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

    public Boolean getEager() {
        return eager;
    }

    public void setEager(Boolean eager) {
        this.eager = eager;
    }

    public boolean isEager() {
        // defaults to true if not configured
        return eager != null ? eager : true;
    }

    public Boolean getSkipDuplicate() {
        return skipDuplicate;
    }

    public void setSkipDuplicate(Boolean skipDuplicate) {
        this.skipDuplicate = skipDuplicate;
    }

    public boolean isSkipDuplicate() {
        // defaults to true if not configured
        return skipDuplicate != null ? skipDuplicate : true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        IdempotentRepository<String> idempotentRepository =
            (IdempotentRepository<String>) resolveMessageIdRepository(routeContext);
        ObjectHelper.notNull(idempotentRepository, "idempotentRepository", this);

        // add as service to CamelContext so we can managed it and it ensures it will be shutdown when camel shutdowns
        routeContext.getCamelContext().addService(idempotentRepository);

        Expression expression = getExpression().createExpression(routeContext);

        return new IdempotentConsumer(expression, idempotentRepository, isEager(), isSkipDuplicate(), childProcessor);
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use
     *
     * @param routeContext  route context
     * @return the repository
     */
    protected IdempotentRepository<?> resolveMessageIdRepository(RouteContext routeContext) {
        if (messageIdRepositoryRef != null) {
            idempotentRepository = routeContext.lookup(messageIdRepositoryRef, IdempotentRepository.class);
        }
        return idempotentRepository;
    }
}
