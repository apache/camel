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
package org.apache.camel.component.jpa;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaCallback;

/**
 * @version $Revision$
 */
public class JpaConsumer extends ScheduledPollConsumer implements BatchConsumer, ShutdownAware {

    private static final transient Log LOG = LogFactory.getLog(JpaConsumer.class);
    private final JpaEndpoint endpoint;
    private final TransactionStrategy template;
    private QueryFactory queryFactory;
    private DeleteHandler<Object> deleteHandler;
    private String query;
    private String namedQuery;
    private String nativeQuery;
    private int maxMessagesPerPoll;
    private volatile ShutdownRunningTask shutdownRunningTask;
    private volatile int pendingExchanges;

    private final class DataHolder {
        private Exchange exchange;
        private Object result;
        private EntityManager manager;
        private DataHolder() {
        }
    }

    public JpaConsumer(JpaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.template = endpoint.createTransactionStrategy();
    }

    @Override
    protected void poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        template.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                Queue<DataHolder> answer = new LinkedList<DataHolder>();

                Query query = getQueryFactory().createQuery(entityManager);
                configureParameters(query);
                List<Object> results = CastUtils.cast(query.getResultList());
                for (Object result : results) {
                    DataHolder holder = new DataHolder();
                    holder.manager = entityManager;
                    holder.result = result;
                    holder.exchange = createExchange(result);
                    answer.add(holder);
                }

                try {
                    processBatch(CastUtils.cast(answer));
                } catch (Exception e) {
                    throw new PersistenceException(e);
                }

                entityManager.flush();
                return null;
            }
        });
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public void processBatch(Queue<Object> exchanges) throws Exception {
        if (exchanges.isEmpty()) {
            return;
        }

        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            LOG.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there was " + total + " messages in this poll.");
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            DataHolder holder = ObjectHelper.cast(DataHolder.class, exchanges.poll());
            EntityManager entityManager = holder.manager;
            Exchange exchange = holder.exchange;
            Object result = holder.result;

            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            if (lockEntity(result, entityManager)) {
                // process the current exchange
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing exchange: " + exchange);
                }
                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    throw new PersistenceException(e);
                }
                getDeleteHandler().deleteObject(entityManager, result);
            }
        }
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    public int getPendingExchangesSize() {
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            return pendingExchanges;
        } else {
            return 0;
        }
    }

    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    // Properties
    // -------------------------------------------------------------------------
    @Override
    public JpaEndpoint getEndpoint() {
        return endpoint;
    }

    public QueryFactory getQueryFactory() {
        if (queryFactory == null) {
            queryFactory = createQueryFactory();
            if (queryFactory == null) {
                throw new IllegalArgumentException("No queryType property configured on this consumer, nor an entityType configured on the endpoint so cannot consume");
            }
        }
        return queryFactory;
    }

    public void setQueryFactory(QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public DeleteHandler<Object> getDeleteHandler() {
        if (deleteHandler == null) {
            deleteHandler = createDeleteHandler();
        }
        return deleteHandler;
    }

    public void setDeleteHandler(DeleteHandler<Object> deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    public String getNamedQuery() {
        return namedQuery;
    }

    public void setNamedQuery(String namedQuery) {
        this.namedQuery = namedQuery;
    }

    public String getNativeQuery() {
        return nativeQuery;
    }

    public void setNativeQuery(String nativeQuery) {
        this.nativeQuery = nativeQuery;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * A strategy method to lock an object with an exclusive lock so that it can
     * be processed
     * 
     * @param entity the entity to be locked
     * @param entityManager entity manager
     * @return true if the entity was locked
     */
    protected boolean lockEntity(Object entity, EntityManager entityManager) {
        if (!getEndpoint().isConsumeDelete() || !getEndpoint().isConsumeLockEntity()) {
            return true;
        }
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Acquiring exclusive lock on entity: " + entity);
            }
            entityManager.lock(entity, LockModeType.WRITE);
            return true;
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to achieve lock on entity: " + entity + ". Reason: " + e, e);
            }
            return false;
        }
    }

    protected QueryFactory createQueryFactory() {
        if (query != null) {
            return QueryBuilder.query(query);
        } else if (namedQuery != null) {
            return QueryBuilder.namedQuery(namedQuery);
        } else if (nativeQuery != null) {
            return QueryBuilder.nativeQuery(nativeQuery);
        } else {
            Class<?> entityType = endpoint.getEntityType();
            
            if (entityType == null) {
                return null;
            } else {
                // Check if we have a property name on the @Entity annotation
                String name = getEntityName(entityType);
                if (name != null) {
                    return QueryBuilder.query("select x from " + name + " x");
                } else {
                    // Remove package name of the entity to be conform with JPA 1.0 spec
                    return QueryBuilder.query("select x from " + entityType.getSimpleName() + " x");
                }
            }
        }
    }
    
    protected String getEntityName(Class<?> clazz) {
        Entity entity = clazz.getAnnotation(Entity.class);

        // Check if the property name has been defined for Entity annotation
        if (entity != null && !entity.name().equals("")) {
            return entity.name();
        } else {
            return null;
        }
    }

    protected DeleteHandler<Object> createDeleteHandler() {
        // look for @Consumed to allow custom callback when the Entity has been consumed
        Class<?> entityType = getEndpoint().getEntityType();
        if (entityType != null) {
            List<Method> methods = ObjectHelper.findMethodsWithAnnotation(entityType, Consumed.class);
            if (methods.size() > 1) {
                throw new IllegalArgumentException("Only one method can be annotated with the @Consumed annotation but found: " + methods);
            } else if (methods.size() == 1) {
                final Method method = methods.get(0);

                return new DeleteHandler<Object>() {
                    public void deleteObject(EntityManager entityManager, Object entityBean) {
                        ObjectHelper.invokeMethod(method, entityBean);
                    }
                };
            }
        }
        if (getEndpoint().isConsumeDelete()) {
            return new DeleteHandler<Object>() {
                public void deleteObject(EntityManager entityManager, Object entityBean) {
                    entityManager.remove(entityBean);
                }
            };
        } else {
            return new DeleteHandler<Object>() {
                public void deleteObject(EntityManager entityManager, Object entityBean) {
                    // do nothing
                }
            };
        }
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
        exchange.getIn().setHeader(JpaConstants.JPA_TEMPLATE, endpoint.getTemplate());
        return exchange;
    }
}
