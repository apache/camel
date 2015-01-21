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
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Label;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Filters out duplicate messages
 */
@Label("eip,endpoints")
@XmlRootElement(name = "idempotentConsumer")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdempotentConsumerDefinition extends ExpressionNode {
    @XmlAttribute(required = true)
    private String messageIdRepositoryRef;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean eager;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean skipDuplicate;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean removeOnFailure;
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
    public String getLabel() {
        return "idempotentConsumer[" + getExpression() + "]";
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Sets the reference name of the message id repository
     *
     * @param messageIdRepositoryRef the reference name of message id repository
     * @return builder
     */
    public IdempotentConsumerDefinition messageIdRepositoryRef(String messageIdRepositoryRef) {
        setMessageIdRepositoryRef(messageIdRepositoryRef);
        return this;
    }

    /**
     * Sets the the message id repository for the idempotent consumer
     *
     * @param idempotentRepository the repository instance of idempotent
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
     * @param eager <tt>true</tt> to add the key before processing, <tt>false</tt> to wait until
     *              the exchange is complete.
     * @return builder
     */
    public IdempotentConsumerDefinition eager(boolean eager) {
        setEager(eager);
        return this;
    }

    /**
     * Sets whether to remove or keep the key on failure.
     * <p/>
     * The default behavior is to remove the key on failure.
     *
     * @param removeOnFailure <tt>true</tt> to remove the key, <tt>false</tt> to keep the key
     *                        if the exchange fails.
     * @return builder
     */
    public IdempotentConsumerDefinition removeOnFailure(boolean removeOnFailure) {
        setRemoveOnFailure(removeOnFailure);
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
     * @param skipDuplicate <tt>true</tt> to skip duplicates, <tt>false</tt> to allow duplicates.
     * @return builder
     */
    public IdempotentConsumerDefinition skipDuplicate(boolean skipDuplicate) {
        setSkipDuplicate(skipDuplicate);
        return this;
    }

    /**
     * Expression used to calculate the correlation key to use for duplicate check.
     * The Exchange which has the same correlation key is regarded as a duplicate and will be rejected.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
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

    public Boolean getSkipDuplicate() {
        return skipDuplicate;
    }

    public void setSkipDuplicate(Boolean skipDuplicate) {
        this.skipDuplicate = skipDuplicate;
    }

    public Boolean getRemoveOnFailure() {
        return removeOnFailure;
    }

    public void setRemoveOnFailure(Boolean removeOnFailure) {
        this.removeOnFailure = removeOnFailure;
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

        // these boolean should be true by default
        boolean eager = getEager() == null || getEager();
        boolean duplicate = getSkipDuplicate() == null || getSkipDuplicate();
        boolean remove = getRemoveOnFailure() == null || getRemoveOnFailure();

        return new IdempotentConsumer(expression, idempotentRepository, eager, duplicate, remove, childProcessor);
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use
     *
     * @param routeContext route context
     * @return the repository
     */
    protected IdempotentRepository<?> resolveMessageIdRepository(RouteContext routeContext) {
        if (messageIdRepositoryRef != null) {
            idempotentRepository = routeContext.mandatoryLookup(messageIdRepositoryRef, IdempotentRepository.class);
        }
        return idempotentRepository;
    }
}
