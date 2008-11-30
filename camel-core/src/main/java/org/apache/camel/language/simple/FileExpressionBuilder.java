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

    public static Expression fileNameExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileName", String.class);
            }

            @Override
            public String toString() {
                return "file:name";
            }
        };
    }

    public static Expression fileNameNoExtensionExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader("CamelFileName", String.class);
                if (name != null) {
                    return name.substring(0, name.lastIndexOf('.'));
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "file:name.noext";
            }
        };
    }

    public static Expression fileNameExtensionExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader("CamelFileName", String.class);
                if (name != null) {
                    return name.substring(name.lastIndexOf('.') + 1);
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "file:name.ext";
            }
        };
    }

    public static Expression fileParentExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileParent", String.class);
            }

            @Override
            public String toString() {
                return "file:parent";
            }
        };
    }

    public static Expression filePathExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFilePath", String.class);
            }

            @Override
            public String toString() {
                return "file:path";
            }
        };
    }

    public static Expression fileAbsolutePathExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
            }

            @Override
            public String toString() {
                return "file:absolute.path";
            }
        };
    }

    public static Expression fileCanoicalPathExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileCanonicalPath", String.class);
            }

            @Override
            public String toString() {
                return "file:canonical.path";
            }
        };
    }

    public static Expression fileSizeExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileLength", Long.class);
            }

            @Override
            public String toString() {
                return "file:length";
            }
        };
    }

    public static Expression fileLastModifiedExpression() {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileLastModified", Date.class);
            }

            @Override
            public String toString() {
                return "file:modified";
            }
        };
    }


    public static Expression dateExpression(final String command, final String pattern) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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

    public static Expression simpleExpression(final String simple) {
        return new Expression() {
            public Object evaluate(Exchange exchange) {
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
