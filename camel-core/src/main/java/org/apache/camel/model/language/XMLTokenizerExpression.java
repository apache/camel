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
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.language.tokenizer.XMLTokenizeLanguage;

/**
 * For expressions and predicates using a body or header tokenizer.
 *
 * @see XMLTokenizeLanguage
 */
@XmlRootElement(name = "xtokenize")
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLTokenizerExpression extends NamespaceAwareExpression {
    @XmlAttribute
    private String headerName;
    @XmlAttribute
    private String mode;
    @XmlAttribute
    private Integer group;

    public XMLTokenizerExpression() {
    }

    public XMLTokenizerExpression(String expression) {
        super(expression);
    }

    @Override
    public String getLanguage() {
        return "xtokenize";
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public Integer getGroup() {
        return group;
    }

    public void setGroup(Integer group) {
        this.group = group;
    }

    @Override
    protected void configureExpression(CamelContext camelContext, Expression expression) {
        super.configureExpression(camelContext, expression);
        if (headerName != null) {
            setProperty(expression, "headerName", headerName);
        }
        if (mode != null) {
            setProperty(expression, "mode", mode);
        }
        if (group != null) {
            setProperty(expression, "group", group);
        }
    }

    @Override
    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
        super.configurePredicate(camelContext, predicate);
        if (headerName != null) {
            setProperty(predicate, "headerName", headerName);
        }
        if (mode != null) {
            setProperty(predicate, "mode", mode);
        }
        if (group != null) {
            setProperty(predicate, "group", group);
        }
    }

    @Override
    public Expression createExpression(CamelContext camelContext) {
        Expression answer = super.createExpression(camelContext);
        return answer;
    }
}