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

package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.ExchangeHelper;

/**
 * A processor which sets multiple variables on the Exchange with an {@link Expression}
 */
public class SetVariablesProcessor extends BaseProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private String id;
    private String routeId;
    private final List<Expression> variableNames;
    private final List<Expression> expressions;

    public SetVariablesProcessor(List<Expression> variableNames, List<Expression> expressions) {
        this.variableNames = variableNames;
        this.expressions = expressions;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            int variableIndex = 0;
            for (Expression expression : expressions) {
                Object newVariable = expression.evaluate(exchange, Object.class);

                if (exchange.getException() != null) {
                    // the expression threw an exception so we should break-out
                    callback.done(true);
                    return true;
                }
                String key = variableNames.get(variableIndex++).evaluate(exchange, String.class);
                ExchangeHelper.setVariable(exchange, key, newVariable);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        StringBuilder sb = new StringBuilder(256);

        sb.append("setVariables[");
        int variableIndex = 0;
        for (Expression expression : expressions) {
            if (variableIndex > 0) {
                sb.append("; ");
            }
            sb.append(variableNames.get(variableIndex++).toString());
            sb.append(", ");
            sb.append(expression.toString());
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public List<Expression> getVariableNames() {
        return variableNames;
    }

    public List<Expression> getExpressions() {
        return expressions;
    }
}
