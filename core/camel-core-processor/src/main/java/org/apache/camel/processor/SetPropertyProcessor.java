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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which sets the property on the exchange with an {@link org.apache.camel.Expression}
 */
public class SetPropertyProcessor extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {
    private String id;
    private String routeId;
    private final Expression propertyName;
    private final Expression expression;

    public SetPropertyProcessor(Expression propertyName, Expression expression) {
        this.propertyName = propertyName;
        this.expression = expression;
        ObjectHelper.notNull(propertyName, "propertyName");
        ObjectHelper.notNull(expression, "expression");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Object newProperty = expression.evaluate(exchange, Object.class);

            if (exchange.getException() != null) {
                // the expression threw an exception so we should break-out
                callback.done(true);
                return true;
            }

            String key = propertyName.evaluate(exchange, String.class);
            exchange.setProperty(key, newProperty);
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
        return "setProperty[" + propertyName + ", " + expression + "]";
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

    public String getPropertyName() {
        return propertyName.toString();
    }

    public Expression getExpression() {
        return expression;
    }

}
