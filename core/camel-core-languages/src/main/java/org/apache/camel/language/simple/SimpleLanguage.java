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
package org.apache.camel.language.simple;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.PredicateToExpressionAdapter;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel simple language.
 */
@Language("simple")
public class SimpleLanguage extends LanguageSupport implements StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleLanguage.class);

    // singleton for expressions without a result type
    private static final SimpleLanguage SIMPLE = new SimpleLanguage();

    // a special prefix to avoid cache clash
    private static final String CACHE_KEY_PREFIX = "@SIMPLE@";

    boolean allowEscape = true;

    // use caches to avoid re-parsing the same expressions over and over again
    private Map<String, Expression> cacheExpression;
    private Map<String, Predicate> cachePredicate;

    /**
     * Default constructor.
     */
    public SimpleLanguage() {
    }

    @Override
    public void init() {
        // setup cache which requires CamelContext to be set first
        if (cacheExpression == null && cachePredicate == null && getCamelContext() != null) {
            int maxSize = CamelContextHelper.getMaximumSimpleCacheSize(getCamelContext());
            if (maxSize > 0) {
                cacheExpression = LRUCacheFactory.newLRUCache(16, maxSize, false);
                cachePredicate = LRUCacheFactory.newLRUCache(16, maxSize, false);
                LOG.debug("Simple language predicate/expression cache size: {}", maxSize);
            } else {
                LOG.debug("Simple language disabled predicate/expression cache");
            }
        }
    }

    @Override
    public void start() {
        if (getCamelContext() != null) {
            SIMPLE.setCamelContext(getCamelContext());
        }
    }

    @Override
    public void stop() {
        if (cachePredicate instanceof LRUCache<String, Predicate> cache) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Clearing simple language predicate cache[size={}, hits={}, misses={}, evicted={}]",
                        cache.size(), cache.getHits(), cache.getMisses(), cache.getEvicted());
            }
            cachePredicate.clear();
        }
        if (cacheExpression instanceof LRUCache<String, Expression> cache) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Clearing simple language expression cache[size={}, hits={}, misses={}, evicted={}]",
                        cache.size(), cache.getHits(), cache.getMisses(), cache.getEvicted());
            }
            cacheExpression.clear();
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        ObjectHelper.notNull(expression, "expression");

        String key = CACHE_KEY_PREFIX + expression;
        Predicate answer = cachePredicate != null ? cachePredicate.get(key) : null;
        if (answer == null) {

            if (isDynamicResource(expression)) {
                // we need to load the resource dynamic based on evaluating the expression via the exchange
                // so create an embedded expression as result
                // need to lazy eval as its a dynamic resource
                final String text = expression;
                return new Predicate() {
                    @Override
                    public boolean matches(Exchange exchange) {
                        String r = ScriptHelper.resolveOptionalExternalScript(getCamelContext(), exchange, text);
                        Predicate pred = SimpleLanguage.this.createPredicate(r);
                        pred.init(getCamelContext());
                        return pred.matches(exchange);
                    }

                    @Override
                    public String toString() {
                        return text;
                    }
                };
            }

            if (isStaticResource(expression)) {
                expression = loadResource(expression);
                key = CACHE_KEY_PREFIX + expression;
            }

            // using the expression cache here with the predicate parser is okay
            SimplePredicateParser parser
                    = new SimplePredicateParser(getCamelContext(), expression, allowEscape, cacheExpression);
            answer = parser.parsePredicate();

            if (cachePredicate != null && answer != null) {
                cachePredicate.put(key, answer);
            }
        }

        return answer;
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        boolean trim = property(boolean.class, properties, 1, true);
        if (trim && expression != null) {
            expression = expression.trim();
        }
        if (expression == null) {
            expression = "${null}";
        }
        return createPredicate(expression);
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        Class<?> resultType = property(Class.class, properties, 0, null);
        boolean trim = property(boolean.class, properties, 1, true);
        if (trim && expression != null) {
            expression = expression.trim();
        }
        if (expression == null) {
            expression = "${null}";
        }
        return createExpression(expression, resultType);
    }

    @Override
    public Expression createExpression(String expression) {
        ObjectHelper.notNull(expression, "expression");

        String key = CACHE_KEY_PREFIX + expression;
        Expression answer = cacheExpression != null ? cacheExpression.get(key) : null;

        if (answer == null) {
            if (isDynamicResource(expression)) {
                // we need to load the resource dynamic based on evaluating the expression via the exchange
                // so create an embedded expression as result need to lazy eval due to dynamic resource
                final String text = expression;
                return new Expression() {
                    @Override
                    public <T> T evaluate(Exchange exchange, Class<T> type) {
                        String r = ScriptHelper.resolveOptionalExternalScript(getCamelContext(), exchange, text);
                        Expression exp = SimpleLanguage.this.createExpression(r);
                        exp.init(getCamelContext());
                        return exp.evaluate(exchange, type);
                    }

                    @Override
                    public String toString() {
                        return text;
                    }
                };
            }

            if (isStaticResource(expression)) {
                // load static resource and re-eval if there are functions
                expression = loadResource(expression);
                key = CACHE_KEY_PREFIX + expression;
            }

            // only parse if there are simple functions
            SimpleExpressionParser parser
                    = new SimpleExpressionParser(getCamelContext(), expression, allowEscape, cacheExpression);
            answer = parser.parseExpression();

            if (cacheExpression != null && answer != null) {
                cacheExpression.put(key, answer);
            }
        }

        return answer;
    }

    public Expression createExpression(String expression, Class<?> resultType) {
        if (resultType == Boolean.class || resultType == boolean.class) {
            // if its a boolean as result then its a predicate
            Predicate predicate = createPredicate(expression);
            return PredicateToExpressionAdapter.toExpression(predicate);
        } else {
            Expression exp = createExpression(expression);
            if (resultType != null) {
                exp = ExpressionBuilder.convertToExpression(exp, resultType);
            }
            return exp;
        }
    }

}
