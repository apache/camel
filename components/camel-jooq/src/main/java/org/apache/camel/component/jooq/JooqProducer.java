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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Result;
import org.jooq.ResultQuery;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;

public class JooqProducer extends DefaultProducer {

    private final Expression expression;
    private JooqEndpoint endpoint;

    public JooqProducer(JooqEndpoint endpoint, Expression expression) {
        super(endpoint);
        this.expression = expression;
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        JooqConfiguration configuration = endpoint.getConfiguration();
        Configuration dbConfig = configuration.getDatabaseConfiguration();
        JooqOperation operation = configuration.getOperation();

        switch (operation) {
        case EXECUTE:
            Query query = expression.evaluate(exchange, Query.class);
            query.attach(dbConfig);
            query.execute();
            break;
        case FETCH:
            ResultQuery resultQuery = expression.evaluate(exchange, ResultQuery.class);
            resultQuery.attach(dbConfig);
            Result result = resultQuery.fetch();
            Message target = exchange.getPattern().isOutCapable() ? exchange.getOut() : exchange.getIn();

            target.setBody(result);
            break;
        case NONE:
            final UpdatableRecord entity = expression.evaluate(exchange, UpdatableRecord.class);
            DSLContext context = DSL.using(dbConfig);
            context.batchStore(entity).execute();
            break;
        default:
            throw new IllegalArgumentException("Wrong operation: " + operation.getValue());
        }
    }
}
