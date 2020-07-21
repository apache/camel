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
package org.apache.camel.reifier.language;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Predicate;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.GroovyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.Hl7TerserExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.MvelExpression;
import org.apache.camel.model.language.OgnlExpression;
import org.apache.camel.model.language.RefExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.SpELExpression;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerAware;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.util.ObjectHelper;

public class ExpressionReifier<T extends ExpressionDefinition> extends AbstractReifier {

    private static final Map<Class<?>, BiFunction<CamelContext, ExpressionDefinition, ExpressionReifier<? extends ExpressionDefinition>>> EXPRESSIONS;

    static {
        Map<Class<?>, BiFunction<CamelContext, ExpressionDefinition, ExpressionReifier<? extends ExpressionDefinition>>> map = new LinkedHashMap<>();
        map.put(ConstantExpression.class, ExpressionReifier::new);
        map.put(ExchangePropertyExpression.class, ExpressionReifier::new);
        map.put(ExpressionDefinition.class, ExpressionReifier::new);
        map.put(GroovyExpression.class, ExpressionReifier::new);
        map.put(HeaderExpression.class, ExpressionReifier::new);
        map.put(Hl7TerserExpression.class, ExpressionReifier::new);
        map.put(JsonPathExpression.class, JsonPathExpressionReifier::new);
        map.put(LanguageExpression.class, ExpressionReifier::new);
        map.put(MethodCallExpression.class, MethodCallExpressionReifier::new);
        map.put(MvelExpression.class, ExpressionReifier::new);
        map.put(OgnlExpression.class, ExpressionReifier::new);
        map.put(RefExpression.class, ExpressionReifier::new);
        map.put(SimpleExpression.class, SimpleExpressionReifier::new);
        map.put(SpELExpression.class, ExpressionReifier::new);
        map.put(TokenizerExpression.class, TokenizerExpressionReifier::new);
        map.put(XMLTokenizerExpression.class, XMLTokenizerExpressionReifier::new);
        map.put(XPathExpression.class, XPathExpressionReifier::new);
        map.put(XQueryExpression.class, XQueryExpressionReifier::new);
        EXPRESSIONS = map;
        ReifierStrategy.addReifierClearer(ExpressionReifier::clearReifiers);
    }

    protected final T definition;

    public ExpressionReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static ExpressionReifier<? extends ExpressionDefinition> reifier(CamelContext camelContext, ExpressionSubElementDefinition definition) {
        return reifier(camelContext, definition.getExpressionType());
    }

    public static ExpressionReifier<? extends ExpressionDefinition> reifier(CamelContext camelContext, ExpressionDefinition definition) {
        BiFunction<CamelContext, ExpressionDefinition, ExpressionReifier<? extends ExpressionDefinition>> reifier = EXPRESSIONS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(camelContext, definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    public static void clearReifiers() {
        EXPRESSIONS.clear();
    }

    public Expression createExpression() {
        Expression expression = definition.getExpressionValue();
        if (expression == null) {
            if (definition.getExpressionType() != null) {
                expression = reifier(camelContext, definition.getExpressionType()).createExpression();
            } else {
                ObjectHelper.notNull(definition.getLanguage(), "language");
                Language language = camelContext.resolveLanguage(definition.getLanguage());
                if (language == null) {
                    throw new NoSuchLanguageException(definition.getLanguage());
                }
                String exp = parseString(definition.getExpression());
                // should be true by default
                boolean isTrim = parseBoolean(definition.getTrim(), true);
                // trim if configured to trim
                if (exp != null && isTrim) {
                    exp = exp.trim();
                }
                // resolve the expression as it may be an external script from
                // the classpath/file etc
                exp = ScriptHelper.resolveOptionalExternalScript(camelContext, exp);
                configureLanguage(language);
                expression = language.createExpression(exp);
                configureExpression(expression);
            }
        }
        // inject CamelContext if its aware
        if (expression instanceof CamelContextAware) {
            ((CamelContextAware) expression).setCamelContext(camelContext);
        }
        expression.init(camelContext);
        return expression;
    }

    public Predicate createPredicate() {
        Predicate predicate = definition.getPredicate();
        if (predicate == null) {
            if (definition.getExpressionType() != null) {
                predicate = reifier(camelContext, definition.getExpressionType()).createPredicate();
            } else if (definition.getExpressionValue() != null) {
                predicate = new ExpressionToPredicateAdapter(definition.getExpressionValue());
            } else if (definition.getExpression() != null) {
                ObjectHelper.notNull(definition.getLanguage(), "language");
                Language language = camelContext.resolveLanguage(definition.getLanguage());
                if (language == null) {
                    throw new NoSuchLanguageException(definition.getLanguage());
                }
                String exp = parseString(definition.getExpression());
                // should be true by default
                boolean isTrim = parseBoolean(definition.getTrim(), true);
                // trim if configured to trim
                if (exp != null && isTrim) {
                    exp = exp.trim();
                }
                // resolve the expression as it may be an external script from
                // the classpath/file etc
                exp = ScriptHelper.resolveOptionalExternalScript(camelContext, exp);
                configureLanguage(language);
                predicate = language.createPredicate(exp);
                configurePredicate(predicate);
            }
        }
        // inject CamelContext if its aware
        if (predicate instanceof CamelContextAware) {
            ((CamelContextAware) predicate).setCamelContext(camelContext);
        }
        predicate.init(camelContext);
        return predicate;
    }

    protected void configureLanguage(Language language) {
    }

    protected void configurePredicate(Predicate predicate) {
        // allows to perform additional logic after the properties has been
        // configured which may be needed
        // in the various camel components outside camel-core
        if (predicate instanceof AfterPropertiesConfigured) {
            ((AfterPropertiesConfigured)predicate).afterPropertiesConfigured(camelContext);
        }
    }

    protected void configureExpression(Expression expression) {
        // allows to perform additional logic after the properties has been
        // configured which may be needed
        // in the various camel components outside camel-core
        if (expression instanceof AfterPropertiesConfigured) {
            ((AfterPropertiesConfigured)expression).afterPropertiesConfigured(camelContext);
        }
    }

    protected void setProperties(Object target, Map<String, Object> properties) {
        properties.entrySet().removeIf(e -> e.getValue() == null);

        PropertyConfigurer configurer = null;
        if (target instanceof PropertyConfigurerAware) {
            configurer = ((PropertyConfigurerAware) target).getPropertyConfigurer(target);
        } else if (target instanceof PropertyConfigurer) {
            configurer = (PropertyConfigurer) target;
        }
        PropertyBindingSupport.build()
                .withConfigurer(configurer)
                .bind(camelContext, target, properties);
    }

}
