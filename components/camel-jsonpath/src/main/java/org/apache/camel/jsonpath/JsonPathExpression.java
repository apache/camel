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
package org.apache.camel.jsonpath;

import java.util.Collection;
import java.util.List;

import com.jayway.jsonpath.Option;
import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.jsonpath.easypredicate.EasyPredicateParser;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonPathExpression extends ExpressionAdapter implements AfterPropertiesConfigured, GeneratedPropertyConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(JsonPathExpression.class);

    private final String expression;
    private JsonPathEngine engine;

    private boolean predicate;
    private Class<?> resultType;
    private boolean suppressExceptions;
    private boolean allowSimple = true;
    private boolean allowEasyPredicate = true;
    private boolean writeAsString;
    private String headerName;
    private Option[] options;

    public JsonPathExpression(String expression) {
        this.expression = expression;
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "resulttype":
            case "resultType":
                setResultType(PropertyConfigurerSupport.property(camelContext, Class.class, value));
                return true;
            case "suppressexceptions":
            case "suppressExceptions":
                setSuppressExceptions(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "allowsimple":
            case "allowSimple":
                setAllowSimple(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "alloweasypredicate":
            case "allowEasyPredicate":
                setAllowEasyPredicate(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "writeasstring":
            case "writeAsString":
                setWriteAsString(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "headername":
            case "headerName":
                setHeaderName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            default:
                return false;
        }
    }

    public boolean isPredicate() {
        return predicate;
    }

    /**
     * Whether to be evaluated as a predicate
     */
    public void setPredicate(boolean predicate) {
        this.predicate = predicate;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * To configure the result type to use
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isSuppressExceptions() {
        return suppressExceptions;
    }

    /**
     * Whether to suppress exceptions such as PathNotFoundException
     */
    public void setSuppressExceptions(boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    public boolean isAllowSimple() {
        return allowSimple;
    }

    /**
     * Whether to allow in inlined simple exceptions in the json path expression
     */
    public void setAllowSimple(boolean allowSimple) {
        this.allowSimple = allowSimple;
    }

    public boolean isAllowEasyPredicate() {
        return allowEasyPredicate;
    }

    /**
     * Whether to allow using the easy predicate parser to pre-parse predicates.
     * See {@link EasyPredicateParser} for more details.
     */
    public void setAllowEasyPredicate(boolean allowEasyPredicate) {
        this.allowEasyPredicate = allowEasyPredicate;
    }

    public boolean isWriteAsString() {
        return writeAsString;
    }

    /**
     * Whether to write the output of each row/element as a JSon String value instead of a Map/POJO value.
     */
    public void setWriteAsString(boolean writeAsString) {
        this.writeAsString = writeAsString;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public Option[] getOptions() {
        return options;
    }

    /**
     * To configure the json path options to use
     */
    public void setOptions(Option[] options) {
        this.options = options;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            Object result = evaluateJsonPath(exchange, engine);
            if (resultType != null) {
                // in some cases we get a single element that is wrapped in a List, so unwrap that
                // if we for example want to grab the single entity and convert that to a int/boolean/String etc
                boolean resultIsCollection = Collection.class.isAssignableFrom(resultType);
                boolean singleElement = result instanceof List && ((List) result).size() == 1;
                if (singleElement && !resultIsCollection) {
                    result = ((List) result).get(0);
                    LOG.trace("Unwrapping result: {} from single element List before converting to: {}", result, resultType);
                }
                return exchange.getContext().getTypeConverter().convertTo(resultType, exchange, result);
            } else {
                return result;
            }
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    @Override
    public void afterPropertiesConfigured(CamelContext camelContext) {
        init();
    }

    public void init() {
        String exp = expression;

        if (predicate && isAllowEasyPredicate()) {
            EasyPredicateParser parser = new EasyPredicateParser();
            exp = parser.parse(expression);

            if (!exp.equals(expression)) {
                LOG.debug("EasyPredicateParser parsed {} -> {}", expression, exp);
            }
        }

        LOG.debug("Initializing {} using: {}", predicate ? "predicate" : "expression", exp);
        try {
            engine = new JsonPathEngine(exp, writeAsString, suppressExceptions, allowSimple, headerName, options);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(exp, e);
        }
    }

    @Override
    public String toString() {
        return "jsonpath[" + expression + "]";
    }

    private Object evaluateJsonPath(Exchange exchange, JsonPathEngine engine) throws Exception {
        return engine.read(exchange);
    }
}
