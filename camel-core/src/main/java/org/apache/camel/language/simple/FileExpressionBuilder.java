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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.FileExchange;
import org.apache.camel.language.bean.BeanLanguage;

/**
 * A helper class for working with <a href="http://activemq.apache.org/camel/expression.html">expressions</a> based
 * on FileExchange.
 */
public final class FileExpressionBuilder {
    private FileExpressionBuilder() {
        // Helper class
    }

    public static <E extends FileExchange> Expression<E> fileNameExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if (exchange.getFile() == null) {
                    return null;
                }
                return exchange.getFile().getName();
            }

            @Override
            public String toString() {
                return "file:name";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> fileNameNoExtensionExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if (exchange.getFile() == null) {
                    return null;
                }
                String name = exchange.getFile().getName();
                return name.substring(0, name.lastIndexOf('.'));
            }

            @Override
            public String toString() {
                return "file:name.noext";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> fileParentExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if (exchange.getFile() == null) {
                    return null;
                }
                return exchange.getFile().getParent();
            }

            @Override
            public String toString() {
                return "file:parent";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> filePathExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if (exchange.getFile() == null) {
                    return null;
                }
                return exchange.getFile().getPath();
            }

            @Override
            public String toString() {
                return "file:path";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> fileAbsoluteExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if (exchange.getFile() == null) {
                    return null;
                }
                return exchange.getFile().getAbsolutePath();
            }

            @Override
            public String toString() {
                return "file:absolute";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> fileCanoicalPathExpression() {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                if (exchange.getFile() == null) {
                    return null;
                }
                try {
                    return exchange.getFile().getCanonicalPath();
                } catch (IOException e) {
                    throw new RuntimeCamelException("Could not get the canonical path for file: " + exchange.getFile(), e);
                }
            }

            @Override
            public String toString() {
                return "file:canonical.path";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> dateExpression(final String command, final String pattern) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                Date date;
                if ("file".equals(command)) {
                    if (exchange.getFile() == null) {
                        return null;
                    }
                    date = new Date(exchange.getFile().lastModified());
                } else if ("now".equals(command)) {
                    date = new Date();
                } else if (command.startsWith("header.") || command.startsWith("in.header.")) {
                    String key = command.substring(command.lastIndexOf(".") + 1);
                    date = exchange.getIn().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Could not find java.util.Date object at " + command);
                    }
                } else if (command.startsWith("out.header.")) {
                    String key = command.substring(command.lastIndexOf(".") + 1);
                    date = exchange.getOut().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Could not find java.util.Date object at " + command);
                    }
                } else {
                    throw new IllegalArgumentException("Command not supported for dateExpression: " + command);
                }

                SimpleDateFormat df = new SimpleDateFormat(pattern);
                return df.format(date);
            }

            @Override
            public String toString() {
                return "date(" + command + ":" + pattern + ")";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> simpleExpression(final String simple) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                // must call evalute to return the nested langauge evaluate when evaluating
                // stacked expressions
                return SimpleLanguage.simple(simple).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "simple(" + simple + ")";
            }
        };
    }

    public static <E extends FileExchange> Expression<E> beanExpression(final String bean) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                // must call evalute to return the nested langauge evaluate when evaluating
                // stacked expressions
                return BeanLanguage.bean(bean).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "bean(" + bean + ")";
            }
        };
    }

}
