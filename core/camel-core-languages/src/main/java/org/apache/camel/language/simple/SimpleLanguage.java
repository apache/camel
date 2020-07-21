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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.PredicateToExpressionAdapter;
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
        // noop
    }

    @Override
    public void stop() {
        if (cachePredicate instanceof LRUCache) {
            if (LOG.isDebugEnabled()) {
                LRUCache cache = (LRUCache) cachePredicate;
                LOG.debug("Clearing simple language predicate cache[size={}, hits={}, misses={}, evicted={}]",
                        cache.size(), cache.getHits(), cache.getMisses(), cache.getEvicted());
            }
            cachePredicate.clear();
        }
        if (cacheExpression instanceof LRUCache) {
            if (LOG.isDebugEnabled()) {
                LRUCache cache = (LRUCache) cacheExpression;
                LOG.debug("Clearing simple language expression cache[size={}, hits={}, misses={}, evicted={}]",
                        cache.size(), cache.getHits(), cache.getMisses(), cache.getEvicted());
            }
            cacheExpression.clear();
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        ObjectHelper.notNull(expression, "expression");

        Predicate answer = cachePredicate != null ? cachePredicate.get(expression) : null;
        if (answer == null) {

            expression = loadResource(expression);

            SimplePredicateParser parser = new SimplePredicateParser(expression, allowEscape, cacheExpression);
            answer = parser.parsePredicate();

            if (cachePredicate != null && answer != null) {
                cachePredicate.put(expression, answer);
            }
        }

        return answer;
    }

    @Override
    public Expression createExpression(String expression) {
        ObjectHelper.notNull(expression, "expression");

        Expression answer = cacheExpression != null ? cacheExpression.get(expression) : null;
        if (answer == null) {

            expression = loadResource(expression);

            SimpleExpressionParser parser = new SimpleExpressionParser(expression, allowEscape, cacheExpression);
            answer = parser.parseExpression();

            if (cacheExpression != null && answer != null) {
                cacheExpression.put(expression, answer);
            }
        }

        return answer;
    }

    /**
     * Creates a new {@link Expression}.
     * <p/>
     * <b>Important:</b> If you need to use a predicate (function to return true|false) then use
     * {@link #predicate(String)} instead.
     */
    public static Expression simple(String expression) {
        return expression(expression);
    }

    /**
     * Creates a new {@link Expression} (or {@link Predicate}
     * if the resultType is a <tt>Boolean</tt>, or <tt>boolean</tt> type).
     */
    public static Expression simple(String expression, Class<?> resultType) {
        return new SimpleLanguage().createExpression(expression, resultType);
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

    /**
     * Creates a new {@link Expression}.
     * <p/>
     * <b>Important:</b> If you need to use a predicate (function to return true|false) then use
     * {@link #predicate(String)} instead.
     */
    public static Expression expression(String expression) {
        return SIMPLE.createExpression(expression);
    }

    /**
     * Creates a new {@link Predicate}.
     */
    public static Predicate predicate(String predicate) {
        return SIMPLE.createPredicate(predicate);
    }

    /**
     * Does the expression include a simple function.
     *
     * @param expression the expression
     * @return <tt>true</tt> if one or more simple function is included in the expression
     */
    public static boolean hasSimpleFunction(String expression) {
        return SimpleTokenizer.hasFunctionStartToken(expression);
    }

}
