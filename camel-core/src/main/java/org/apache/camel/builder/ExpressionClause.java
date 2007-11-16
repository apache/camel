/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import java.util.Map;

import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.language.MethodCall;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;

/**
 * Represents an expression clause within the DSL which when the expression is complete
 * the clause continues to another part of the DSL
 *
 * @version $Revision: 1.1 $
 */
public class ExpressionClause<T> extends ExpressionType {
    private T result;
    private String language;

    public static <T extends ExpressionNode> ExpressionClause<T> createAndSetExpression(T result) {
        ExpressionClause<T> clause = new ExpressionClause<T>(result);
        result.setExpression(clause);
        return clause;
    }

    public ExpressionClause(T result) {
        this.result = result;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Evaluates an expression using the
     * <a href="http://activemq.apache.org/camel/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression value.
     *
     * @param bean the name of the bean looked up the registry
     * @return the builder to continue processing the DSL
     */
    public T bean(String bean) {
        MethodCall expression = new MethodCall(bean);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an expression using the
     * <a href="http://activemq.apache.org/camel/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression value.
     *
     * @param bean the name of the bean looked up the registry
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T bean(String bean, String method) {
        MethodCall expression = new MethodCall(bean, method);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates the  <a href="http://activemq.apache.org/camel/el.html">EL Language from JSP and JSF</a>
     * using the <a href="http://activemq.apache.org/camel/juel.html">JUEL library</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T el(String text) {
        return language("el", text);
    }

    /**
     * Evaluates a <a href="http://activemq.apache.org/camel/groovy.html">Groovy expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return language("groovy", text);
    }

    /**
     * Evaluates a <a href="http://activemq.apache.org/camel/java-script.html">JavaScript expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T javaScript(String text) {
        return language("js", text);
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/ognl.html">OGNL expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return language("ognl", text);
    }

    /**
     * Evaluates a <a href="http://activemq.apache.org/camel/php.html">PHP expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T php(String text) {
        return language("php", text);
    }

    /**
     * Evaluates a <a href="http://activemq.apache.org/camel/python.html">Python expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T python(String text) {
        return language("python", text);
    }

    /**
     * Evaluates a <a href="http://activemq.apache.org/camel/ruby.html">Ruby expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ruby(String text) {
        return language("ruby", text);
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/sql.html">SQL expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T sql(String text) {
        return language("sql", text);
    }

    /**
     * Evaluates a <a href="http://activemq.apache.org/camel/simple.html">Simple expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T simple(String text) {
        return language("simple", text);
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xpath.html">XPath expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return language("xpath", text);
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xpath.html">XPath expression</a>
     * with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expressiopn
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class resultType) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xpath.html">XPath expression</a>
     * with the specified result type and set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class resultType, Namespaces namespaces) {
        return xpath(text, resultType, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xpath.html">XPath expression</a>
     * with the specified result type and set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class resultType, Map<String,String> namespaces) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xpath.html">XPath expression</a>
     * with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Namespaces namespaces) {
        return xpath(text, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xpath.html">XPath expression</a>
     * with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Map<String,String> namespaces) {
        XPathExpression expression = new XPathExpression(text);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }


    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xquery.html">XQuery expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return language("xquery", text);
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xquery.html">XQuery expression</a>
     * with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expressiopn
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class resultType) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xquery.html">XQuery expression</a>
     * with the specified result type and set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class resultType, Namespaces namespaces) {
        return xquery(text, resultType, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xquery.html">XQuery expression</a>
     * with the specified result type and set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class resultType, Map<String,String> namespaces) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xquery.html">XQuery expression</a>
     * with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Namespaces namespaces) {
        return xquery(text, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://activemq.apache.org/camel/xquery.html">XQuery expression</a>
     * with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Map<String,String> namespaces) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param language   the name of the language
     * @param expression the expression in the given language
     * @return the builder to continue processing the DSL
     */
    public T language(String language, String expression) {
        setLanguage(language);
        setExpression(expression);
        return result;
    }

    // Properties
    //-------------------------------------------------------------------------
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
