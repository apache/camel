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

package org.apache.camel.language.simple;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.language.IllegalSyntaxException;
import org.apache.camel.language.constant.ConstantLanguage;

/**
 * A helper class for working with <a href="http://activemq.apache.org/camel/expression.html">expressions</a> based
 * on files.
 * <p/>
 * This expression expects the headers from the {@link FileLanguage} on the <b>IN</b> message.
 *
 * @see org.apache.camel.language.simple.FileLanguage
 */
public final class FileExpressionBuilder {

    private FileExpressionBuilder() {
        // Helper class
    }

    public static <E extends Exchange> Expression<E> fileNameExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getHeader("CamelFileName", String.class);
            }

            @Override
            public String toString() {
                return "file:name";
            }
        };
    }

    public static <E extends Exchange> Expression<E> fileNameNoExtensionExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                String name = exchange.getIn().getHeader("CamelFileName", String.class);
                if (name.lastIndexOf(".") != -1) {
                    return name.substring(0, name.lastIndexOf('.'));
                } else {
                    // name does not have extension
                    return name;
                }
            }

            @Override
            public String toString() {
                return "file:name.noext";
            }
        };
    }

    public static <E extends Exchange> Expression<E> fileParentExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getHeader("CamelFileParent", String.class);
            }

            @Override
            public String toString() {
                return "file:parent";
            }
        };
    }

    public static <E extends Exchange> Expression<E> filePathExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getHeader("CamelFilePath", String.class);
            }

            @Override
            public String toString() {
                return "file:path";
            }
        };
    }

    public static <E extends Exchange> Expression<E> fileAbsolutePathExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
            }

            @Override
            public String toString() {
                return "file:absolute.path";
            }
        };
    }

    public static <E extends Exchange> Expression<E> fileCanoicalPathExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getHeader("CamelFileCanonicalPath", String.class);
            }

            @Override
            public String toString() {
                return "file:canonical.path";
            }
        };
    }

    public static <E extends Exchange> Expression<E> fileSizeExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return exchange.getIn().getHeader("CamelFileLength", Long.class);
            }

            @Override
            public String toString() {
                return "file:length";
            }
        };
    }

    public static <E extends Exchange> Expression<E> dateExpression(final String command, final String pattern) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if ("file".equals(command)) {
                    Date date = exchange.getIn().getHeader("CamelFileLastModified", Date.class);
                    if (date != null) {
                        SimpleDateFormat df = new SimpleDateFormat(pattern);
                        return df.format(date);
                    } else {
                        return null;
                    }
                }
                // must call evaluate to return the nested language evaluate when evaluating
                // stacked expressions
                return ExpressionBuilder.dateExpression(command, pattern).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "date(" + command + ":" + pattern + ")";
            }
        };
    }

    public static <E extends Exchange> Expression<E> simpleExpression(final String simple) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                // must call evaluate to return the nested language evaluate when evaluating
                // stacked expressions
                try {
                    return SimpleLanguage.simple(simple).evaluate(exchange);
                } catch (IllegalSyntaxException e) {
                    // fallback to constant so end users can enter a fixed filename
                    return ConstantLanguage.constant(simple).evaluate(exchange);
                }
            }

            @Override
            public String toString() {
                return "simple(" + simple + ")";
            }
        };
    }

}
