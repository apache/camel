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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Predicate;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.language.CSimpleExpression;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.GroovyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.Hl7TerserExpression;
import org.apache.camel.model.language.JoorExpression;
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

    // for custom reifiers
    private static final Map<Class<?>, BiFunction<CamelContext, ExpressionDefinition, ExpressionReifier<? extends ExpressionDefinition>>> EXPRESSIONS
            = new HashMap<>(0);

    protected final T definition;

    public ExpressionReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static void registerReifier(
            Class<?> processorClass,
            BiFunction<CamelContext, ExpressionDefinition, ExpressionReifier<? extends ExpressionDefinition>> creator) {
        if (EXPRESSIONS.isEmpty()) {
            ReifierStrategy.addReifierClearer(ExpressionReifier::clearReifiers);
        }
        EXPRESSIONS.put(processorClass, creator);
    }

    public static ExpressionReifier<? extends ExpressionDefinition> reifier(
            CamelContext camelContext, ExpressionSubElementDefinition definition) {
        return reifier(camelContext, definition.getExpressionType());
    }

    public static ExpressionReifier<? extends ExpressionDefinition> reifier(
            CamelContext camelContext, ExpressionDefinition definition) {

        ExpressionReifier<? extends ExpressionDefinition> answer = null;
        if (!EXPRESSIONS.isEmpty()) {
            // custom take precedence
            BiFunction<CamelContext, ExpressionDefinition, ExpressionReifier<? extends ExpressionDefinition>> reifier
                    = EXPRESSIONS.get(definition.getClass());
            if (reifier != null) {
                answer = reifier.apply(camelContext, definition);
            }
        }
        if (answer == null) {
            answer = coreReifier(camelContext, definition);
        }
        if (answer == null) {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
        return answer;
    }

    private static ExpressionReifier<? extends ExpressionDefinition> coreReifier(
            CamelContext camelContext, ExpressionDefinition definition) {
        if (definition instanceof ConstantExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof CSimpleExpression) {
            return new CSimpleExpressionReifier(camelContext, definition);
        } else if (definition instanceof DatasonnetExpression) {
            return new DatasonnetExpressionReifier(camelContext, definition);
        } else if (definition instanceof ExchangePropertyExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof GroovyExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof HeaderExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof Hl7TerserExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof JoorExpression) {
            return new JoorExpressionReifier(camelContext, definition);
        } else if (definition instanceof JsonPathExpression) {
            return new JsonPathExpressionReifier(camelContext, definition);
        } else if (definition instanceof LanguageExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof MethodCallExpression) {
            return new MethodCallExpressionReifier(camelContext, definition);
        } else if (definition instanceof MvelExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof OgnlExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof RefExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof SimpleExpression) {
            return new SimpleExpressionReifier(camelContext, definition);
        } else if (definition instanceof SpELExpression) {
            return new ExpressionReifier<>(camelContext, definition);
        } else if (definition instanceof TokenizerExpression) {
            return new TokenizerExpressionReifier(camelContext, definition);
        } else if (definition instanceof XMLTokenizerExpression) {
            return new XMLTokenizerExpressionReifier(camelContext, definition);
        } else if (definition instanceof XPathExpression) {
            return new XPathExpressionReifier(camelContext, definition);
        } else if (definition instanceof XQueryExpression) {
            return new XQueryExpressionReifier(camelContext, definition);
        } else if (definition instanceof ExpressionDefinition) {
            return new ExpressionReifier<>(camelContext, definition);
        }
        return null;
    }

    public static void clearReifiers() {
        EXPRESSIONS.clear();
    }

    public boolean isResolveOptionalExternalScriptEnabled() {
        return true;
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
                if (isResolveOptionalExternalScriptEnabled()) {
                    exp = ScriptHelper.resolveOptionalExternalScript(camelContext, exp);
                }
                configureLanguage(language);
                expression = createExpression(language, exp);
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
                predicate = createPredicate(language, exp);
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

    protected Expression createExpression(Language language, String exp) {
        return language.createExpression(exp);
    }

    protected Predicate createPredicate(Language language, String exp) {
        return language.createPredicate(exp);
    }

    protected void configureLanguage(Language language) {
    }

    protected void configurePredicate(Predicate predicate) {
        // allows to perform additional logic after the properties has been
        // configured which may be needed
        // in the various camel components outside camel-core
        if (predicate instanceof AfterPropertiesConfigured) {
            ((AfterPropertiesConfigured) predicate).afterPropertiesConfigured(camelContext);
        }
    }

    protected void configureExpression(Expression expression) {
        // allows to perform additional logic after the properties has been
        // configured which may be needed
        // in the various camel components outside camel-core
        if (expression instanceof AfterPropertiesConfigured) {
            ((AfterPropertiesConfigured) expression).afterPropertiesConfigured(camelContext);
        }
    }

    @Deprecated
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
                .withIgnoreCase(true)
                .bind(camelContext, target, properties);
    }

}
