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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.jooq.Query;
import org.jooq.ResultQuery;

@UriEndpoint(firstVersion = "3.0.0", scheme = "jooq", syntax = "jooq://entityType/operation", title = "JOOQ", label = "jooq")
public class JooqEndpoint extends ScheduledPollEndpoint {

    private Expression producerExpression;

    @UriParam
    private JooqConfiguration configuration;

    public JooqEndpoint(String uri, String remaining, JooqComponent component, JooqConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;

        initConfiguration(remaining);
    }

    private void initConfiguration(String remaining) {
        if (remaining == null) {
            return;
        }

        String[] parts = remaining.split("/");
        if (parts.length == 0 || parts.length > 2) {
            throw new IllegalArgumentException("Unexpected URI format. Expected ... , found '" + remaining + "'");
        }

        Class<?> type = ObjectHelper.loadClass(parts[0]);
        if (type != null) {
            configuration.setEntityType(type);
        }

        if (parts.length > 1) {
            JooqOperation operation = JooqOperation.getByValue(parts[1]);
            if (operation != null) {
                configuration.setOperation(operation);
            } else {
                throw new IllegalArgumentException("Wrong operation: " + parts[1]);
            }
        }
    }

    @Override
    public Producer createProducer() {
        return new JooqProducer(this, getProducerExpression());
    }

    public JooqConfiguration getConfiguration() {
        return configuration;
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
            type = null;
        }

        if (type == null) {
            return ExpressionBuilder.bodyExpression();
        } else {
            EndpointAnnotationProcessor:
            return new Expression() {
                public Object evaluate(Exchange exchange, Class asType) {
                    Object answer = exchange.getIn().getBody(type);
                    if (answer == null) {
                        Object defaultValue = exchange.getIn().getBody();
                        if (defaultValue != null) {
                            throw RuntimeCamelException.wrapRuntimeCamelException(new NoTypeConversionAvailableException(defaultValue, type));
                        }

                        answer = exchange.getContext().getInjector().newInstance(type);
                    }
                    return answer;
                }
            };
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        return new JooqConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public void setConfiguration(JooqConfiguration configuration) {
        this.configuration = configuration;
    }
}
