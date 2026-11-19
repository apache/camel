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

import net.thisptr.jackson.jq.v2.core.Environment;
import net.thisptr.jackson.jq.v2.core.JsonQuery;
import net.thisptr.jackson.jq.v2.spi.exception.JsonQueryException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.support.BodyFacts;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.NullNode;

public class JqExpression extends ExpressionAdapter implements ExpressionResultTypeAware {

    private static final String JACKSON2_NODE_PACKAGE = "com.fasterxml.jackson.databind.node.";
    private static final String JACKSON2_DATABIND_PACKAGE = "com.fasterxml.jackson.databind.";

    private final String expression;

    private Environment<JsonNode> environment;
    private String resultTypeName;
    private Class<?> resultType;
    private JsonQuery<JsonNode> query;
    private TypeConverter typeConverter;
    private Expression source;

    public JqExpression(String expression) {
        this(null, expression);
    }

    public JqExpression(Environment<JsonNode> environment, String expression) {
        this.environment = environment;
        this.expression = expression;
    }

    @Override
    public void init(CamelContext camelContext) {
        // avoid initializing multiple times
        if (this.query == null) {
            super.init(camelContext);

            if (this.environment == null) {
                JqLanguage lan = (JqLanguage) camelContext.resolveLanguage("jq");
                this.environment = lan.getEnvironment();
            }

            this.typeConverter = camelContext.getTypeConverter();
            try {
                this.query = this.environment.compile(this.expression);
            } catch (JsonQueryException e) {
                throw new ExpressionIllegalSyntaxException(this.expression, e);
            }

            if (resultTypeName != null && (resultType == null || resultType == Object.class)) {
                resultType = camelContext.getClassResolver().resolveClass(resultTypeName);
            }
            if (resultType == null || resultType == Object.class) {
                resultType = JsonNode.class;
            }
        }
        if (this.source == null) {
            source = ExpressionBuilder.bodyExpression();
        }
    }

    public Environment<JsonNode> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment<JsonNode> environment) {
        this.environment = environment;
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

    public Expression getSource() {
        return source;
    }

    public void setSource(Expression source) {
        this.source = source;
    }

    @Override
    public boolean matches(Exchange exchange) {
        final Object value = evaluate(exchange, Object.class);

        if (value instanceof BooleanNode booleanNode) {
            return booleanNode.asBoolean();
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

            this.query.apply(payload, outputs::add);

            if (outputs.size() == 1) {
                JsonNode out = outputs.get(0);
                // special if null
                if (out.isNull()) {
                    return null;
                }

                // no need to convert output
                if (resultType == JsonNode.class) {
                    return out;
                }
                return convertResult(exchange, out);
            } else if (outputs.size() > 1) {
                // no need to convert outputs
                if (resultType == JsonNode.class) {
                    return outputs;
                }

                return outputs.stream()
                        .filter(o -> !o.isNull()) // skip null
                        .map(item -> convertResult(exchange, item))
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
        Object value = source.evaluate(exchange, Object.class);
        // Jackson 3 cannot convert a Jackson 2 node; the text of a node of either line is its JSON
        JsonNode payload = typeConverter.convertTo(JsonNode.class, exchange,
                isJackson2Node(value) ? value.toString() : value);
        // if body is stream cached then reset, so we can re-read it again
        MessageHelper.resetStreamCache(exchange.getMessage());
        if (payload != null && payload.isMissingNode()) {
            // an empty or blank source holds no JSON value: read it as null, as jackson-jq 1 did
            payload = NullNode.getInstance();
        }
        if (payload == null) {
            // tell apart a source that is null from one that is set but holds no JSON
            if (value == null) {
                throw new InvalidPayloadException(exchange, JsonNode.class, exchange.getIn(), nullSourceHint(exchange));
            }
            throw new InvalidPayloadException(exchange, JsonNode.class);
        }
        return payload;
    }

    private Object convertResult(Exchange exchange, JsonNode node) {
        // Jackson 3 cannot convert to a Jackson 2 node: go through its JSON text, as for the input
        Object value = resultType.getName().startsWith(JACKSON2_DATABIND_PACKAGE) ? node.toString() : node;
        return this.typeConverter.convertTo(resultType, exchange, value);
    }

    private static boolean isJackson2Node(Object value) {
        return value != null && value.getClass().getName().startsWith(JACKSON2_NODE_PACKAGE);
    }

    private String nullSourceHint(Exchange exchange) {
        if ("body".equals(source.toString())) {
            // a timer alone, or a jq step placed before the file was read: nothing to evaluate. What the body is, the
            // step that left it so, and how to read the data (CAMEL-24838, CAMEL-25749)
            String hint = BodyFacts.hint(exchange, BodyFacts.Need.A_BODY);
            return "the jq expression got no message body to evaluate: " + BodyFacts.describe(exchange)
                   + (hint != null ? ": " + hint : "");
        }
        return "the jq expression got no input from " + source + " to evaluate, it is null: set it before the step,"
               + " or leave source unset to use the message body";
    }
}
