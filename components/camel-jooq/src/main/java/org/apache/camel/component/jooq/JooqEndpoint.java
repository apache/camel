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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.jooq.Query;
import org.jooq.ResultQuery;

/**
 * Store and retrieve Java objects from an SQL database using JOOQ.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "jooq", syntax = "jooq:entityType", title = "JOOQ",
             category = { Category.DATABASE })
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   description = "One row of the table of the entityType per message, as the jOOQ"
                                                 + " UpdatableRecord of that class"),
           producerIn = @BodyType(value = Carrier.POJO, dependsOn = "query",
                                  description = "A jOOQ object, by the operation: a record of the entityType to store for"
                                                + " NONE, an org.jooq.Query for EXECUTE, an org.jooq.ResultQuery (a select)"
                                                + " for FETCH. Text, a Map or a List fails: there is no conversion to them."
                                                + " With the query option the SQL is that query and the body is not read"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = @BodyType.When(option = "operation", value = "FETCH", carriers = Carrier.LIST,
                                                             javaType = "org.jooq.Result"),
                                   description = "NONE stores the record and EXECUTE runs the query, both leave the body"
                                                 + " unchanged. FETCH gives the org.jooq.Result of the query, a List of its"
                                                 + " records, which is empty for no row"))
public class JooqEndpoint extends ScheduledPollEndpoint {

    private Expression producerExpression;

    @UriParam
    private JooqConfiguration configuration;

    public JooqEndpoint(String uri, JooqComponent component, JooqConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        return new JooqProducer(this, getProducerExpression());
    }

    public JooqConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JooqConfiguration configuration) {
        this.configuration = configuration;
    }

    public Expression getProducerExpression() {
        if (producerExpression == null) {
            producerExpression = createProducerExpression();
        }
        return producerExpression;
    }

    protected Expression createProducerExpression() {
        final Class<?> type;

        switch (configuration.getOperation()) {
            case NONE:
                type = configuration.getEntityType();
                break;
            case EXECUTE:
                type = Query.class;
                break;
            case FETCH:
                type = ResultQuery.class;
                break;
            default:
                throw new UnsupportedOperationException("Operation: " + configuration.getOperation());
        }

        return new Expression() {
            public Object evaluate(Exchange exchange, Class asType) {
                Object answer = exchange.getIn().getBody(type);
                if (answer == null) {
                    Object defaultValue = exchange.getIn().getBody();
                    if (defaultValue != null) {
                        throw RuntimeCamelException
                                .wrapRuntimeCamelException(new NoTypeConversionAvailableException(defaultValue, type));
                    }

                    answer = exchange.getContext().getInjector().newInstance(type);
                }
                return answer;
            }
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        JooqConsumer consumer = new JooqConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

}
