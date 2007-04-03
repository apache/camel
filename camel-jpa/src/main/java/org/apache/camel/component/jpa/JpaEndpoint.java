/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jpa;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.springframework.orm.jpa.JpaTemplate;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * @version $Revision$
 */
public class JpaEndpoint extends DefaultEndpoint<Exchange> {
    private final JpaComponent component;
    private JpaTemplate template;
    private Expression<Exchange> producerExpression;
    private int maximumResults = -1;
    private Class<?> entityType;

    public JpaEndpoint(String uri, JpaComponent component) {
        super(uri, component);
        this.component = component;
        this.template = component.getTemplate();
    }

    public Exchange createExchange() {
        return new DefaultExchange(getContext());
    }

    public Producer<Exchange> createProducer() throws Exception {
        return startService(new JpaProducer(this, getProducerExpression()));
    }

    public Consumer<Exchange> createConsumer(Processor<Exchange> processor) throws Exception {
        return startService(new JpaConsumer(this, processor));
    }


    // Properties
    //-------------------------------------------------------------------------
    public JpaTemplate getTemplate() {
        return template;
    }

    public void setTemplate(JpaTemplate template) {
        this.template = template;
    }

    public Expression<Exchange> getProducerExpression() {
        if (producerExpression == null) {
            producerExpression = createProducerExpression();
        }
        return producerExpression;
    }

    public void setProducerExpression(Expression<Exchange> producerExpression) {
        this.producerExpression = producerExpression;
    }

    public int getMaximumResults() {
        return maximumResults;
    }

    public void setMaximumResults(int maximumResults) {
        this.maximumResults = maximumResults;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public void setEntityType(Class<?> entityType) {
        this.entityType = entityType;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected EntityManager createEntityManager() {
        return component.createEntityManager();
    }

    protected TransactionStrategy createTransactionStrategy() {
        EntityManagerFactory emf = component.getEntityManagerFactory();
        return JpaTemplateTransactionStrategy.newInstance(emf, getTemplate());
        //return new DefaultTransactionStrategy(emf);
    }

    protected Expression<Exchange> createProducerExpression() {
        final Class<?> type = getEntityType();
        if (type == null) {
            return ExpressionBuilder.bodyExpression();
        }
        else {
            return new Expression<Exchange>() {
                public Object evaluate(Exchange exchange) {
                    Object answer = exchange.getIn().getBody(type);
                    if (answer == null) {
                        Object defaultValue = exchange.getIn().getBody();
                        if (defaultValue != null) {
                            throw new NoTypeConversionAvailableException(defaultValue, type);
                        }

                        // if we don't have a body then
                        // lets instantiate and inject a new instance
                        answer = exchange.getContext().getInjector().newInstance(type);
                    }
                    return answer;
                }
            };
        }
    }
}
