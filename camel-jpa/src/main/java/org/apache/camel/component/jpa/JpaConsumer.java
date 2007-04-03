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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.PollingConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import java.util.List;

/**
 * @version $Revision$
 */
public class JpaConsumer extends PollingConsumer<Exchange> {
    private static final transient Log log = LogFactory.getLog(JpaConsumer.class);
    private final JpaEndpoint endpoint;
    private final EntityManager entityManager;
    private QueryFactory queryFactory;
    private DeleteHandler<Object> deleteHandler;
    private EntityTransaction transaction;

    public JpaConsumer(JpaEndpoint endpoint, Processor<Exchange> processor, EntityManager entityManager) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.entityManager = entityManager;
    }

    /**
     * Invoked whenever we should be polled
     */
    public synchronized void run() {
        log.debug("Starting to poll for new database entities to process");
        transaction = entityManager.getTransaction();
        transaction.begin();

        try {
            Query query = getQueryFactory().createQuery(this);
            configureParameters(query);
            List results = query.getResultList();
            for (Object result : results) {
                if (log.isDebugEnabled()) {
                    log.debug("Processing new entity: " + result);
                }

                if (lockEntity(result)) {
                    // lets turn the result into an exchange and fire it into the processor
                    Exchange exchange = createExchange(result);
                    getProcessor().onExchange(exchange);
                    getDeleteHandler().deleteObject(this, result);
                }
            }

            transaction.commit();
            transaction = null;
        }
        catch (RuntimeException e) {
            log.warn("Caught: " + e, e);
            if (transaction != null) {
                transaction.rollback();
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public JpaEndpoint getEndpoint() {
        return endpoint;
    }

    public QueryFactory getQueryFactory() {
        if (queryFactory == null) {
            queryFactory = createQueryFactory();
        }
        return queryFactory;
    }

    public void setQueryFactory(QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public DeleteHandler getDeleteHandler() {
        if (deleteHandler == null) {
            deleteHandler = createDeleteHandler();
        }
        return deleteHandler;
    }

    public void setDeleteHandler(DeleteHandler deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    @Override
    protected synchronized void doStop() throws Exception {
        if (transaction != null) {
            transaction.rollback();
        }
        entityManager.close();
        super.doStop();
    }

    /**
     * A strategy method to lock an object with an exclusive lock so that it can be processed
     *
     * @param entity the entity to be locked
     * @return true if the entity was locked
     */
    protected boolean lockEntity(Object entity) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Acquiring exclusive lock on entity: " + entity);
            }
            entityManager.lock(entity, LockModeType.WRITE);
            return true;
        }
        catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to achieve lock on entity: " + entity + ". Reason: " + e, e);
            }
            return false;
        }
    }

    protected QueryFactory createQueryFactory() {
        Class<?> entityType = endpoint.getEntityType();
        if (entityType == null) {
            return null;
        }
        else {
            return QueryBuilder.query("select x from " + entityType.getName() + " x");
        }
    }

    protected DeleteHandler<Object> createDeleteHandler() {
        // TODO auto-discover an annotation in the entity bean to indicate the process completed method call?

        return new DeleteHandler<Object>() {
            public void deleteObject(JpaConsumer consumer, Object entityBean) {
                consumer.getEntityManager().remove(entityBean);
            }
        };
    }

    protected void configureParameters(Query query) {
        int maxResults = endpoint.getMaximumResults();
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
    }

    protected Exchange createExchange(Object result) {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(result);
        return exchange;
    }
}
