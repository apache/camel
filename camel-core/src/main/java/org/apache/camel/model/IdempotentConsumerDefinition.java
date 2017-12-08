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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Filters out duplicate messages
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "idempotentConsumer")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdempotentConsumerDefinition extends ExpressionNode {

    @XmlAttribute(required = true)
    private String messageIdRepositoryRef;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean eager;
    @XmlAttribute
    private Boolean completionEager;
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
     * Sets whether to complete the idempotent consumer eager or when the exchange is done.
     * <p/>
     * If this option is <tt>true</tt> to complete eager, then the idempotent consumer will trigger its completion
     * when the exchange reached the end of the block of the idempotent consumer pattern. So if the exchange
     * is continued routed after the block ends, then whatever happens there does not affect the state.
     * <p/>
     * If this option is <tt>false</tt> (default) to <b>not</b> complete eager, then the idempotent consumer
     * will complete when the exchange is done being routed. So if the exchange is continued routed after the block ends,
     * then whatever happens there <b>also</b> affect the state.
     * For example if the exchange failed due to an exception, then the state of the idempotent consumer will be a rollback.
     *
     * @param completionEager   whether to complete eager or complete when the exchange is done
     * @return builder
     */
    public IdempotentConsumerDefinition completionEager(boolean completionEager) {
        setCompletionEager(completionEager);
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

    public Boolean getCompletionEager() {
        return completionEager;
    }

    public void setCompletionEager(Boolean completionEager) {
        this.completionEager = completionEager;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        IdempotentRepository<String> idempotentRepository = resolveMessageIdRepository(routeContext);
        ObjectHelper.notNull(idempotentRepository, "idempotentRepository", this);

        Expression expression = getExpression().createExpression(routeContext);

        // these boolean should be true by default
        boolean eager = getEager() == null || getEager();
        boolean duplicate = getSkipDuplicate() == null || getSkipDuplicate();
        boolean remove = getRemoveOnFailure() == null || getRemoveOnFailure();

        // these boolean should be false by default
        boolean completionEager = getCompletionEager() != null && getCompletionEager();

        return new IdempotentConsumer(expression, idempotentRepository, eager, completionEager, duplicate, remove, childProcessor);
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use
     *
     * @param routeContext route context
     * @return the repository
     */
    @SuppressWarnings("unchecked")
    protected <T> IdempotentRepository<T> resolveMessageIdRepository(RouteContext routeContext) {
        if (messageIdRepositoryRef != null) {
            idempotentRepository = routeContext.mandatoryLookup(messageIdRepositoryRef, IdempotentRepository.class);
        }
        return (IdempotentRepository<T>)idempotentRepository;
    }
}
