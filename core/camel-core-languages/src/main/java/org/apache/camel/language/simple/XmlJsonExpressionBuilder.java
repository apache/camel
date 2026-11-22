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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.json.JsonStringHelper;
import org.apache.camel.util.xml.XmlStringHelper;

/**
 * Expression builder for the XML and JSON functions of the simple language: compact, strip, canonicalize and check that
 * a payload is well-formed.
 * <p>
 * The XML is parsed with DOCTYPE declarations disallowed (no entities, no access to files or the network), see
 * {@link XmlStringHelper}.
 */
public final class XmlJsonExpressionBuilder {

    private XmlJsonExpressionBuilder() {
    }

    /**
     * Removes the whitespace between the elements of XML, or the tokens of JSON (the inverse of {@code pretty}). A
     * value that is not XML or JSON, or not well-formed, is returned as-is.
     */
    public static Expression compactExpression(final String expression) {
        return new ValueExpression("compact", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                String text = value.strip();
                if (text.startsWith("{") && text.endsWith("}") || text.startsWith("[") && text.endsWith("]")) {
                    String answer = JsonStringHelper.compact(text);
                    return answer != null ? answer : value;
                } else if (text.startsWith("<") && text.endsWith(">")) {
                    try {
                        return XmlStringHelper.compact(text);
                    } catch (Exception e) {
                        return value;
                    }
                }
                return value;
            }
        };
    }

    /**
     * Removes the XML declaration.
     */
    public static Expression stripXmlDeclarationExpression(final String expression) {
        return new ValueExpression("stripXmlDeclaration", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                return XmlStringHelper.stripDeclaration(value);
            }
        };
    }

    /**
     * Removes the comments of XML. A value that is not well-formed XML is returned as-is.
     */
    public static Expression stripXmlCommentsExpression(final String expression) {
        return new ValueExpression("stripXmlComments", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                try {
                    return XmlStringHelper.stripComments(value);
                } catch (Exception e) {
                    return value;
                }
            }
        };
    }

    /**
     * Removes the namespace declarations and prefixes of XML. A value that is not well-formed XML is returned as-is.
     */
    public static Expression stripXmlNamespacesExpression(final String expression) {
        return new ValueExpression("stripXmlNamespaces", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                try {
                    return XmlStringHelper.stripNamespaces(value);
                } catch (Exception e) {
                    return value;
                }
            }
        };
    }

    /**
     * Canonicalizes XML (C14N). A value that is not well-formed XML fails the evaluation, as a value returned as-is
     * would give a false answer when canonical forms are compared.
     *
     * @param exclusive whether to use Exclusive XML Canonicalization, or (inclusive) Canonical XML 1.0
     */
    public static Expression canonicalXmlExpression(final String expression, final boolean exclusive) {
        return new ValueExpression("canonicalXml", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                try {
                    return XmlStringHelper.canonicalize(value, exclusive);
                } catch (Exception e) {
                    throw new ExpressionEvaluationException(
                            this, "Cannot canonicalize the XML: " + e.getMessage(), exchange, e);
                }
            }

            @Override
            public String toString() {
                String text = super.toString();
                return exclusive ? text : text.substring(0, text.length() - 1) + ",inclusive)";
            }
        };
    }

    /**
     * Whether the value is well-formed XML. XML with a DOCTYPE declaration is not accepted.
     */
    public static Expression isValidXmlExpression(final String expression) {
        return new ValueExpression("isValidXml", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                return XmlStringHelper.isWellFormed(value);
            }

            @Override
            Object applyNull() {
                return false;
            }
        };
    }

    /**
     * Whether the value is valid JSON.
     */
    public static Expression isValidJsonExpression(final String expression) {
        return new ValueExpression("isValidJson", expression) {
            @Override
            Object apply(Exchange exchange, String value) {
                return JsonStringHelper.isValid(value);
            }

            @Override
            Object applyNull() {
                return false;
            }
        };
    }

    /**
     * A function of the message body, or of the given expression, as a String.
     */
    private abstract static class ValueExpression extends ExpressionAdapter {

        private final String name;
        private final String expression;
        private Expression exp;

        ValueExpression(String name, String expression) {
            this.name = name;
            this.expression = expression;
        }

        @Override
        public void init(CamelContext context) {
            if (expression != null) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }
        }

        @Override
        public Object evaluate(Exchange exchange) {
            String value;
            if (exp != null) {
                value = exp.evaluate(exchange, String.class);
            } else {
                value = exchange.getMessage().getBody(String.class);
            }
            return value != null ? apply(exchange, value) : applyNull();
        }

        abstract Object apply(Exchange exchange, String value);

        Object applyNull() {
            return null;
        }

        @Override
        public String toString() {
            return name + "(" + (expression != null ? expression : "") + ")";
        }
    }
}
