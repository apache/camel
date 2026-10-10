/*
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
package org.apache.camel.component.jooq;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;

public class JooqConsumer extends ScheduledBatchPollingConsumer {

    private static final class DataHolder {
        private Exchange exchange;
        private UpdatableRecord<?> record;
        private boolean consumed;

        private DataHolder() {
        }
    }

    public JooqConsumer(JooqEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public JooqEndpoint getEndpoint() {
        return (JooqEndpoint) super.getEndpoint();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected int poll() throws Exception {
        JooqConfiguration configuration = getEndpoint().getConfiguration();
        Configuration dbConfig = configuration.getDatabaseConfiguration();
        Class<?> entityType = configuration.getEntityType();
        DSLContext context = DSL.using(dbConfig);

        Queue<DataHolder> answer = new LinkedList<>();
        Result<UpdatableRecord<?>> results = context.selectFrom(getTable(entityType)).fetch();

        // okay we have some response from jooq so lets mark the consumer as ready
        forceConsumerAsReady();

        List<DataHolder> holders = new ArrayList<>(results.size());
        for (UpdatableRecord<?> result : results) {
            DataHolder holder = new DataHolder();
            holder.exchange = createExchange(result);
            holder.record = result;
            holders.add(holder);
        }
        answer.addAll(holders);

        int messagePolled = processBatch(CastUtils.cast(answer));

        if (configuration.isConsumeDelete()) {
            // only delete the entities whose exchange was processed successfully, so a failed (or not processed)
            // entity stays in the table and is consumed again by the next poll
            List<UpdatableRecord<?>> consumed = new ArrayList<>(holders.size());
            for (DataHolder holder : holders) {
                if (holder.consumed) {
                    consumed.add(holder.record);
                }
            }
            if (!consumed.isEmpty()) {
                context.batchDelete(consumed).execute();
            }
        }

        return messagePolled;
    }

    protected Exchange createExchange(Object result) {
        // not auto released: the pooled exchange factory resets an auto released exchange when its unit of work is done,
        // before processBatch reads whether it failed (it releases the exchange itself)
        Exchange exchange = createExchange(false);
        exchange.getIn().setBody(result);
        return exchange;
    }

    private Table getTable(Class<?> entityType) {
        UpdatableRecord object = (UpdatableRecord) ObjectHelper.newInstance(entityType);
        return object.getTable();
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        // only loop while we are allowed to run: when a graceful shutdown starts, the remaining entities are not
        // processed, so they are not deleted and the next start consumes them
        for (int index = 0; index < total && isBatchAllowed(); index++) {
            DataHolder holder = org.apache.camel.util.ObjectHelper.cast(DataHolder.class, exchanges.poll());
            Exchange exchange = holder.exchange;
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
            holder.consumed = !exchange.isFailed() && !exchange.isRollbackOnly();
            releaseExchange(exchange, false);
        }

        return total;
    }
}
