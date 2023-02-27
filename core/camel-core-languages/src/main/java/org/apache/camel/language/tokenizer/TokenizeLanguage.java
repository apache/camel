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
package org.apache.camel.language.tokenizer;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.SingleInputLanguageSupport;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A language for tokenizer expressions.
 * <p/>
 * This tokenizer language can operator in the following modes:
 * <ul>
 * <li>default - using a single tokenizer</li>
 * <li>pair - using both start and end tokens</li>
 * <li>xml - using both start and end tokens in XML mode, support inheriting namespaces</li>
 * </ul>
 * The default mode supports the <tt>headerName</tt> and <tt>regex</tt> options. Where as the pair mode only supports
 * <tt>token</tt> and <tt>endToken</tt>. And the <tt>xml</tt> mode supports the <tt>inheritNamespaceTagName</tt> option.
 */
@org.apache.camel.spi.annotations.Language("tokenize")
public class TokenizeLanguage extends SingleInputLanguageSupport implements PropertyConfigurer {

    private String token;
    private String endToken;
    private String inheritNamespaceTagName;
    private boolean regex;
    private boolean xml;
    private boolean includeTokens;
    private String group;
    private String groupDelimiter;
    private boolean skipFirst;

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "token":
                setToken(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "endtoken":
            case "endToken":
                setEndToken(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "inheritnamespacetagname":
            case "inheritNamespaceTagName":
                setInheritNamespaceTagName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "headername":
            case "headerName":
                setHeaderName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "propertyname":
            case "propertyName":
                setPropertyName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "regex":
                setRegex(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "xml":
                setXml(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "includetokens":
            case "includeTokens":
                setIncludeTokens(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "group":
                setGroup(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "groupdelimiter":
            case "groupDelimiter":
                setGroupDelimiter(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "skipfirst":
            case "skipFirst":
                setSkipFirst(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            default:
                return false;
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    /**
     * Creates a tokenize expression.
     */
    public Expression createExpression() {
        ObjectHelper.notNull(token, "token");

        // validate some invalid combinations
        if (endToken != null && inheritNamespaceTagName != null) {
            throw new IllegalArgumentException("Cannot have both xml and pair tokenizer enabled.");
        }
        if (isXml() && (endToken != null || includeTokens)) {
            throw new IllegalArgumentException("Cannot have both xml and pair tokenizer enabled.");
        }
        if (endToken == null && includeTokens) {
            throw new IllegalArgumentException("The option includeTokens requires endToken to be specified.");
        }

        Expression answer = null;
        if (isXml()) {
            answer = ExpressionBuilder.tokenizeXMLExpression(token, inheritNamespaceTagName);
        } else if (endToken != null) {
            answer = ExpressionBuilder.tokenizePairExpression(token, endToken, includeTokens);
        }

        if (answer == null) {
            // use the regular tokenizer
            final Expression exp = ExpressionBuilder.singleInputExpression(getHeaderName(), getPropertyName());
            if (regex) {
                answer = ExpressionBuilder.regexTokenizeExpression(exp, token);
            } else {
                answer = ExpressionBuilder.tokenizeExpression(exp, token);
            }
            if (group == null && skipFirst) {
                // wrap in skip first (if group then it has its own skip first logic)
                answer = ExpressionBuilder.skipFirstExpression(answer);
            }
        }

        // if group then wrap answer in group expression
        if (group != null) {
            if (isXml()) {
                answer = ExpressionBuilder.groupXmlIteratorExpression(answer, group);
            } else {
                String delim = groupDelimiter != null ? groupDelimiter : token;
                answer = ExpressionBuilder.groupIteratorExpression(answer, delim, group, skipFirst);
            }
        }

        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return answer;
    }

    @Override
    public Expression createExpression(String expression) {
        if (ObjectHelper.isNotEmpty(expression)) {
            this.token = expression;
        }
        return createExpression();
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        TokenizeLanguage answer = new TokenizeLanguage();
        answer.setToken(property(String.class, properties, 0, token));
        answer.setEndToken(property(String.class, properties, 1, endToken));
        answer.setInheritNamespaceTagName(
                property(String.class, properties, 2, inheritNamespaceTagName));
        answer.setHeaderName(property(String.class, properties, 3, getHeaderName()));
        answer.setGroupDelimiter(property(String.class, properties, 4, groupDelimiter));
        answer.setRegex(property(boolean.class, properties, 5, regex));
        answer.setXml(property(boolean.class, properties, 6, xml));
        answer.setIncludeTokens(property(boolean.class, properties, 7, includeTokens));
        answer.setGroup(property(String.class, properties, 8, group));
        answer.setSkipFirst(property(boolean.class, properties, 9, skipFirst));
        answer.setPropertyName(property(String.class, properties, 10, getPropertyName()));
        return answer.createExpression(expression);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEndToken() {
        return endToken;
    }

    public void setEndToken(String endToken) {
        this.endToken = endToken;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public String getInheritNamespaceTagName() {
        return inheritNamespaceTagName;
    }

    public void setInheritNamespaceTagName(String inheritNamespaceTagName) {
        this.inheritNamespaceTagName = inheritNamespaceTagName;
    }

    public boolean isXml() {
        return xml;
    }

    public void setXml(boolean xml) {
        this.xml = xml;
    }

    public boolean isIncludeTokens() {
        return includeTokens;
    }

    public void setIncludeTokens(boolean includeTokens) {
        this.includeTokens = includeTokens;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = "0".equals(group) ? null : group;
    }

    public String getGroupDelimiter() {
        return groupDelimiter;
    }

    public void setGroupDelimiter(String groupDelimiter) {
        this.groupDelimiter = groupDelimiter;
    }

    public boolean isSkipFirst() {
        return skipFirst;
    }

    public void setSkipFirst(boolean skipFirst) {
        this.skipFirst = skipFirst;
    }

}
