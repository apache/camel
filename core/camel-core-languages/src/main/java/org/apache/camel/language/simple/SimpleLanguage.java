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
 * A <a href="http://camel.apache.org/simple.html">simple language</a>
 * which maps simple property style notations to access headers and bodies.
 * Examples of supported expressions are:
 * <ul>
 * <li>exchangeId to access the exchange id</li>
 * <li>id to access the inbound message id</li>
 * <li>in.body or body to access the inbound body</li>
 * <li>in.body.OGNL or body.OGNL to access the inbound body using an OGNL expression</li>
 * <li>mandatoryBodyAs(&lt;classname&gt;) to convert the in body to the given type, will throw exception if not possible to convert</li>
 * <li>bodyAs(&lt;classname&gt;) to convert the in body to the given type, will return null if not possible to convert</li>
 * <li>headerAs(&lt;key&gt;, &lt;classname&gt;) to convert the in header to the given type, will return null if not possible to convert</li>
 * <li>out.body to access the inbound body</li>
 * <li>in.header.foo or header.foo to access an inbound header called 'foo'</li>
 * <li>in.header.foo[bar] or header.foo[bar] to access an inbound header called 'foo' as a Map and lookup the map with 'bar' as key</li>
 * <li>in.header.foo.OGNL or header.OGNL to access an inbound header called 'foo' using an OGNL expression</li>
 * <li>out.header.foo to access an outbound header called 'foo'</li>
 * <li>property.foo to access the exchange property called 'foo'</li>
 * <li>property.foo.OGNL to access the exchange property called 'foo' using an OGNL expression</li>
 * <li>sys.foo to access the system property called 'foo'</li>
 * <li>sysenv.foo to access the system environment called 'foo'</li>
 * <li>exception.messsage to access the exception message</li>
 * <li>threadName to access the current thread name</li>
 * <li>date:&lt;command&gt; evaluates to a Date object
 *     Supported commands are: <tt>now</tt> for current timestamp,
 *     <tt>in.header.xxx</tt> or <tt>header.xxx</tt> to use the Date object in the in header.
 *     <tt>out.header.xxx</tt> to use the Date object in the out header.
 *     <tt>property.xxx</tt> to use the Date object in the exchange property.
 *     <tt>file</tt> for the last modified timestamp of the file (available with a File consumer).
 *     Command accepts offsets such as: <tt>now-24h</tt> or <tt>in.header.xxx+1h</tt> or even <tt>now+1h30m-100</tt>.
 * </li>
 * <li>date:&lt;command&gt;:&lt;pattern&gt; for date formatting using {@link java.text.SimpleDateFormat} patterns</li>
 * <li>date-with-timezone:&lt;command&gt;:&lt;timezone&gt;:&lt;pattern&gt; for date formatting using {@link java.text.SimpleDateFormat} timezones and patterns</li>
 * <li>bean:&lt;bean expression&gt; to invoke a bean using the
 * {@link org.apache.camel.language.bean.BeanLanguage BeanLanguage}</li>
 * <li>properties:&lt;[locations]&gt;:&lt;key&gt; for using property placeholders using the
 *     {@link org.apache.camel.component.properties.PropertiesComponent}.
 *     The locations parameter is optional and you can enter multiple locations separated with comma.
 * </li>
* </ul>
 * <p/>
 * The simple language supports OGNL notation when accessing either body or header.
 * <p/>
 * The simple language now also includes file language out of the box which means the following expression is also
 * supported:
 * <ul>
 *   <li><tt>file:name</tt> to access the file name (is relative, see note below))</li>
 *   <li><tt>file:name.noext</tt> to access the file name with no extension</li>
 *   <li><tt>file:name.ext</tt> to access the file extension</li>
 *   <li><tt>file:ext</tt> to access the file extension</li>
 *   <li><tt>file:onlyname</tt> to access the file name (no paths)</li>
 *   <li><tt>file:onlyname.noext</tt> to access the file name (no paths) with no extension </li>
 *   <li><tt>file:parent</tt> to access the parent file name</li>
 *   <li><tt>file:path</tt> to access the file path name</li>
 *   <li><tt>file:absolute</tt> is the file regarded as absolute or relative</li>
 *   <li><tt>file:absolute.path</tt> to access the absolute file path name</li>
 *   <li><tt>file:length</tt> to access the file length as a Long type</li>
 *   <li><tt>file:size</tt> to access the file length as a Long type</li>
 *   <li><tt>file:modified</tt> to access the file last modified as a Date type</li>
 * </ul>
 * The <b>relative</b> file is the filename with the starting directory clipped, as opposed to <b>path</b> that will
 * return the full path including the starting directory.
 * <br/>
 * The <b>only</b> file is the filename only with all paths clipped.
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
