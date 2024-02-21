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
package org.apache.camel.component.nitrite.operation;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.nitrite.AbstractNitriteOperation;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.dizitart.no2.Document;

public abstract class AbstractPayloadAwareOperation extends AbstractNitriteOperation {
    private Expression expression;

    protected AbstractPayloadAwareOperation(Object body) {
        this.expression = ExpressionBuilder.constantExpression(body);
    }

    protected AbstractPayloadAwareOperation(Expression expression) {
        this.expression = expression;
    }

    protected AbstractPayloadAwareOperation() {
        this.expression = ExpressionBuilder.bodyExpression();
    }

    protected Object getPayload(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        Class<?> targetClass = endpoint.getRepositoryClass() != null ? endpoint.getRepositoryClass() : Document.class;
        Object payload = expression.evaluate(exchange, Object.class);

        return endpoint.getCamelContext().getTypeConverter().mandatoryConvertTo(targetClass, exchange, payload);
    }
}
