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
package org.apache.camel.language.jq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderOrPropertyException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExpressionAdapter;

public class JqExpression extends ExpressionAdapter implements ExpressionResultTypeAware {

    private final String expression;

    private Scope scope;
    private String resultTypeName;
    private Class<?> resultType;
    private JsonQuery query;
    private TypeConverter typeConverter;

    private String headerName;
    private String propertyName;

    public JqExpression(String expression) {
        this(null, expression);
    }

    public JqExpression(Scope scope, String expression) {
        this.scope = scope;
        this.expression = expression;
    }

    @Override
    public void init(CamelContext camelContext) {
        super.init(camelContext);

        this.typeConverter = camelContext.getTypeConverter();

        if (this.scope == null) {
            this.scope = CamelContextHelper.findSingleByType(camelContext, Scope.class);
        }

        if (this.scope == null) {
            // if no scope is explicit set or no scope is found in the registry,
            // then a local scope is created and functions are loaded from the
            // jackson-jq library and the component.
            this.scope = Scope.newEmptyScope();

            JqFunctions.load(camelContext, scope);
            JqFunctions.loadLocal(scope);
        }

        try {
            this.query = JsonQuery.compile(this.expression, Versions.JQ_1_6);
        } catch (JsonQueryException e) {
            throw new RuntimeException(e);
        }

        if (resultTypeName != null && (resultType == null || resultType == Object.class)) {
            resultType = camelContext.getClassResolver().resolveClass(resultTypeName);
        }
        if (resultType == null || resultType == Object.class) {
            resultType = JsonNode.class;
        }
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    @Override
    public String getExpressionText() {
        return this.expression;
    }

    @Override
    public Class<?> getResultType() {
        return this.resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     * </p>
     * It has as higher precedent than the propertyName if both are set.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Name of property to use as input, instead of the message body.
     * </p>
     * It has a lower precedent than the headerName if both are set.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public boolean matches(Exchange exchange) {
        final Object value = evaluate(exchange, Object.class);

        if (value instanceof BooleanNode) {
            return ((BooleanNode) value).asBoolean();
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }

        return false;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        if (this.query == null) {
            return null;
        }

        try {
            JqFunctions.EXCHANGE_LOCAL.set(exchange);

            final List<JsonNode> outputs = new ArrayList<>(1);
            final JsonNode payload = getPayload(exchange);

            this.query.apply(scope, payload, outputs::add);

            if (outputs.size() == 1) {
                // no need to convert output
                if (resultType == JsonNode.class) {
                    return outputs.get(0);
                }

                return this.typeConverter.convertTo(resultType, exchange, outputs.get(0));
            } else if (outputs.size() > 1) {
                // no need to convert outputs
                if (resultType == JsonNode.class) {
                    return outputs;
                }

                return outputs.stream()
                        .map(item -> this.typeConverter.convertTo(resultType, exchange, item))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        } finally {
            JqFunctions.EXCHANGE_LOCAL.remove();
        }

        return null;
    }

    /**
     * Determines the payload by looking at heders, properties and finally the payload.
     *
     * @param  exchange the {@link Exchange} being processed
     * @return          the {@link JsonNode} to be processed by the expression
     */
    private JsonNode getPayload(Exchange exchange) throws Exception {
        JsonNode payload = null;

        if (headerName == null && propertyName == null) {
            payload = exchange.getMessage().getBody(JsonNode.class);
            if (payload == null) {
                throw new InvalidPayloadException(exchange, JsonNode.class);
            }
        } else {
            if (headerName != null) {
                payload = exchange.getMessage().getHeader(headerName, JsonNode.class);
            }
            if (payload == null && propertyName != null) {
                payload = exchange.getProperty(propertyName, JsonNode.class);
            }
            if (payload == null) {
                throw new NoSuchHeaderOrPropertyException(exchange, headerName, propertyName, JsonNode.class);
            }
        }

        return payload;
    }
}
