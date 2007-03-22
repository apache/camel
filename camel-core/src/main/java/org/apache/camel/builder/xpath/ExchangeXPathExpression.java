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
package org.apache.camel.builder.xpath;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.StringReader;

/**
 * An {@link Expression} which uses XPath to perform the evaluation
 *
 * @version $Revision$
 */
public class ExchangeXPathExpression<E extends Exchange> implements Expression<E> {
    private final XPathExpression expression;
    private final MessageVariableResolver variableResolver;
    private Class documentType;
    private String text;
    private QName resultType;

    public ExchangeXPathExpression(XPathBuilder builder, XPathExpression expression, MessageVariableResolver variableResolver) {
        this.expression = expression;
        this.variableResolver = variableResolver;
        this.documentType = builder.getDocumentType();
        this.text = builder.getText();
        this.resultType = builder.getResultType();
    }

    public Object evaluate(E exchange) {
        return evaluateAs(exchange, resultType);
    }

    public Class getDocumentType() {
        return documentType;
    }

    public String getText() {
        return text;
    }

    public MessageVariableResolver getVariableResolver() {
        return variableResolver;
    }

    /**
     * Evaluates the expression as the given result type
     */
    protected synchronized Object evaluateAs(E exchange, QName resultType) {
        variableResolver.setExchange(exchange);
        try {
            Object document = getDocument(exchange);
            if (resultType != null) {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource) document;
                    return expression.evaluate(inputSource, resultType);
                }
                else {
                    return expression.evaluate(document, resultType);
                }
            }
            else {
                if (document instanceof InputSource) {
                    InputSource inputSource = (InputSource) document;
                    return expression.evaluate(inputSource);
                }
                else {
                    return expression.evaluate(document);
                }
            }
        }
        catch (XPathExpressionException e) {
            throw new InvalidXPathExpression(getText(), e);
        }
    }

    /**
     * Strategy method to extract the document from the exchange
     */
    protected Object getDocument(E exchange) {
        Message in = exchange.getIn();
        Class type = getDocumentType();
        Object answer = null;
        if (type != null) {
            answer = in.getBody(type);
        }
        if (answer == null) {
            answer = in.getBody();
        }

        // lets try coerce some common types into something JAXP can deal with
        if (answer instanceof String) {
            answer = new InputSource(new StringReader(answer.toString()));
        }
        return answer;
    }
}

