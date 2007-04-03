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
import org.apache.camel.impl.DefaultConsumer;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;

/**
 * @version $Revision$
 */
public class JpaConsumer extends DefaultConsumer<Exchange> {
    private final JpaEndpoint endpoint;
    private final EntityManager entityManager;
    private QueryFactory queryFactory;
    private DeleteHandler<Object> deleteHandler;

    public JpaConsumer(JpaEndpoint endpoint, Processor<Exchange> processor, EntityManager entityManager) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.entityManager = entityManager;
    }

    /**
     * Invoked whenever we should be polled
     */
    public void run() {
        Query query = queryFactory.createQuery(this);
        configureParameters(query);
        List results = query.getResultList();
        for (Object result : results) {
            // lets turn the result into an exchange and fire it into the processor
            Exchange exchange = createExchange(result);
            getProcessor().onExchange(exchange);
            deleteHandler.deleteObject(this, result);
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
        return deleteHandler;
    }

    public void setDeleteHandler(DeleteHandler deleteHandler) {
        this.deleteHandler = deleteHandler;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    @Override
    protected void doStop() throws Exception {
        entityManager.close();
        super.doStop();
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
