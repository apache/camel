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
public class FileExpressionBuilder {

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
