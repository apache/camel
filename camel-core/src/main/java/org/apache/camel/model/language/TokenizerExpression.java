/**
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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.RouteContext;
import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.builder.Builder.header;
import static org.apache.camel.builder.ExpressionBuilder.regexTokenizeExpression;
import static org.apache.camel.builder.ExpressionBuilder.tokenizeExpression;
import static org.apache.camel.builder.PredicateBuilder.toPredicate;

/**
 * For expressions and predicates using a body or header tokenzier
 *
 * @version $Revision$
 */
@XmlRootElement(name = "tokenizer")
@XmlAccessorType(XmlAccessType.FIELD)
public class TokenizerExpression extends ExpressionType {
    @XmlAttribute(required = true)
    private String token;
    @XmlAttribute(required = false)
    private String headerName;
    @XmlAttribute(required = false)
    private Boolean regex;

    public TokenizerExpression() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public Boolean getRegex() {
        return regex;
    }

    @Override
    public Expression createExpression(RouteContext routeContext) {
        Expression exp = headerName == null ? body() : header(headerName);
        if (regex != null && regex) {
            return regexTokenizeExpression(exp, token);
        } else {
            return tokenizeExpression(exp, token);
        }
    }

    @Override
    public Predicate createPredicate(RouteContext routeContext) {
        return toPredicate(createExpression(routeContext));
    }

    @Override
    public String toString() {
        return "tokenize{" + (headerName != null ? "header: " + headerName : "body()") + " using token: " + token + "}";
    }
}