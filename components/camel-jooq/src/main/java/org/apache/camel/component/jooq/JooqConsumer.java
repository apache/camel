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

import java.util.LinkedList;
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
        private Object result;

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

        for (UpdatableRecord result : results) {
            DataHolder holder = new DataHolder();
            holder.result = result;
            holder.exchange = createExchange(result);
            answer.add(holder);
        }

        int messagePolled = processBatch(CastUtils.cast(answer));

        if (configuration.isConsumeDelete()) {
            context.batchDelete(results).execute();
        }

        return messagePolled;
    }

    protected Exchange createExchange(Object result) {
        Exchange exchange = createExchange(true);
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

        if (isBatchAllowed()) {
            for (int i = 0; i < total; i++) {
                DataHolder holder = org.apache.camel.util.ObjectHelper.cast(DataHolder.class, exchanges.poll());
                getProcessor().process(holder.exchange);
            }
        }

        return total;
    }
}
