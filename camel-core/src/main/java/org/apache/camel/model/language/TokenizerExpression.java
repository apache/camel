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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.tokenizer.TokenizeLanguage;

/**
 * For expressions and predicates using a body or header tokenizer
 *
 * @version 
 */
@XmlRootElement(name = "tokenize")
@XmlAccessorType(XmlAccessType.FIELD)
public class TokenizerExpression extends ExpressionDefinition {
    @XmlAttribute(required = true)
    private String token;
    @XmlAttribute(required = false)
    private String headerName;
    @XmlAttribute(required = false)
    private Boolean regex;

    public TokenizerExpression() {
    }

    @Override
    public String getLanguage() {
        return "tokenize";
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
    public Expression createExpression(CamelContext camelContext) {
        TokenizeLanguage language = new TokenizeLanguage();
        language.setToken(token);
        language.setHeaderName(headerName);
        if (regex != null) {
            language.setRegex(regex);
        }
        return language.createExpression();
    }

    @Override
    public String toString() {
        return "tokenize{" + (headerName != null ? "header: " + headerName : "body()") + " using token: " + token + "}";
    }
}