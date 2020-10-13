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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.SkipIterator;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;

/**
 * Expression builder used by the simple language.
 */
public final class SimpleExpressionBuilder {

    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])([^+-]+)");

    private SimpleExpressionBuilder() {
    }

    /**
     * Returns the expression for the exchanges inbound message header invoking methods defined in a simple OGNL
     * notation
     *
     * @param ognl methods to invoke on the header in a simple OGNL syntax
     */
    public static Expression headersOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(
                ognl, "headerOgnl(" + ognl + ")",
                (exchange, exp) -> {
                    String text = exp.evaluate(exchange, String.class);
                    return exchange.getIn().getHeader(text);
                });
    }

    /**
     * Returns the message history (including exchange details or not)
     */
    public static Expression messageHistoryExpression(final boolean detailed) {
        return new ExpressionAdapter() {

            private ExchangeFormatter formatter;

            @Override
            public void init(CamelContext context) {
                if (detailed) {
                    // use the exchange formatter to log exchange details
                    formatter = getOrCreateExchangeFormatter(context);
                }
            }

            public Object evaluate(Exchange exchange) {
                return MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, false);
            }

            private ExchangeFormatter getOrCreateExchangeFormatter(CamelContext camelContext) {
                if (formatter == null) {
                    Set<ExchangeFormatter> formatters = camelContext.getRegistry().findByType(ExchangeFormatter.class);
                    if (formatters != null && formatters.size() == 1) {
                        formatter = formatters.iterator().next();
                    } else {
                        // setup exchange formatter to be used for message history dump
                        DefaultExchangeFormatter def = new DefaultExchangeFormatter();
                        def.setShowExchangeId(true);
                        def.setMultiline(true);
                        def.setShowHeaders(true);
                        def.setStyle(DefaultExchangeFormatter.OutputStyle.Fixed);
                        try {
                            Integer maxChars = CamelContextHelper.parseInteger(camelContext,
                                    camelContext.getGlobalOption(Exchange.LOG_DEBUG_BODY_MAX_CHARS));
                            if (maxChars != null) {
                                def.setMaxChars(maxChars);
                            }
                        } catch (Exception e) {
                            throw RuntimeCamelException.wrapRuntimeCamelException(e);
                        }
                        formatter = def;
                    }
                }
                return formatter;
            }

            @Override
            public String toString() {
                return "messageHistory(" + detailed + ")";
            }
        };
    }

    /**
     * Returns an iterator to collate (iterate) the given expression
     */
    public static Expression collateExpression(final String expression, final int group) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                // first use simple then create the group expression
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                exp = ExpressionBuilder.groupIteratorExpression(exp, null, "" + group, false);
                exp.init(context);
            }

            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "collate(" + expression + "," + group + ")";
            }
        };
    }

    /**
     * Returns an iterator to skip (iterate) the given expression
     */
    public static Expression skipExpression(final String expression, final int number) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            public Object evaluate(Exchange exchange) {
                return skipIteratorExpression(exp, number).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "skip(" + expression + "," + number + ")";
            }
        };
    }

    /**
     * Returns a random number between min and max (exclusive)
     */
    public static Expression randomExpression(final String min, final String max) {
        return new ExpressionAdapter() {
            private Expression exp1;
            private Expression exp2;

            public Object evaluate(Exchange exchange) {
                int num1 = exp1.evaluate(exchange, Integer.class);
                int num2 = exp2.evaluate(exchange, Integer.class);
                Random random = new Random();
                int randomNum = random.nextInt(num2 - num1) + num1;
                return randomNum;
            }

            @Override
            public void init(CamelContext context) {
                exp1 = ExpressionBuilder.simpleExpression(min);
                exp1.init(context);
                exp2 = ExpressionBuilder.simpleExpression(max);
                exp2.init(context);
            }

            @Override
            public String toString() {
                return "random(" + min + "," + max + ")";
            }
        };
    }

    /**
     * Returns a random number between 0 and max (exclusive)
     */
    public static Expression randomExpression(final int max) {
        return randomExpression(0, max);
    }

    /**
     * Returns a random number between min and max (exclusive)
     */
    public static Expression randomExpression(final int min, final int max) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Random random = new Random();
                int randomNum = random.nextInt(max - min) + min;
                return randomNum;
            }

            @Override
            public String toString() {
                return "random(" + min + "," + max + ")";
            }
        };
    }

    public static Expression fileNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            }

            @Override
            public String toString() {
                return "file:name";
            }
        };
    }

    public static Expression fileOnlyNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY, String.class);
                if (answer == null) {
                    answer = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                    answer = FileUtil.stripPath(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "file:onlyname";
            }
        };
    }

    public static Expression fileNameNoExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.stripExt(name);
            }

            @Override
            public String toString() {
                return "file:name.noext";
            }
        };
    }

    public static Expression fileNameNoExtensionSingleExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.stripExt(name, true);
            }

            @Override
            public String toString() {
                return "file:name.noext.single";
            }
        };
    }

    public static Expression fileOnlyNameNoExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = fileOnlyNameExpression().evaluate(exchange, String.class);
                return FileUtil.stripExt(name);
            }

            @Override
            public String toString() {
                return "file:onlyname.noext";
            }
        };
    }

    public static Expression fileOnlyNameNoExtensionSingleExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = fileOnlyNameExpression().evaluate(exchange, String.class);
                return FileUtil.stripExt(name, true);
            }

            @Override
            public String toString() {
                return "file:onlyname.noext.single";
            }
        };
    }

    public static Expression fileExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.onlyExt(name);
            }

            @Override
            public String toString() {
                return "file:ext";
            }
        };
    }

    public static Expression fileExtensionSingleExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.onlyExt(name, true);
            }

            @Override
            public String toString() {
                return "file:ext.single";
            }
        };
    }

    public static Expression fileParentExpression() {
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
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
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
            }

            @Override
            public String toString() {
                return "file:absolute.path";
            }
        };
    }

    public static Expression fileAbsoluteExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolute", Boolean.class);
            }

            @Override
            public String toString() {
                return "file:absolute";
            }
        };
    }

    public static Expression fileSizeExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_LENGTH, Long.class);
            }

            @Override
            public String toString() {
                return "file:length";
            }
        };
    }

    public static Expression fileLastModifiedExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
            }

            @Override
            public String toString() {
                return "file:modified";
            }
        };
    }

    public static Expression dateExpression(final String command) {
        return dateExpression(command, null, null);
    }

    public static Expression dateExpression(final String command, final String pattern) {
        return dateExpression(command, null, pattern);
    }

    public static Expression dateExpression(final String commandWithOffsets, final String timezone, final String pattern) {
        final String command = commandWithOffsets.split("[+-]", 2)[0].trim();
        // Capture optional time offsets
        final List<Long> offsets = new ArrayList<>();
        Matcher offsetMatcher = OFFSET_PATTERN.matcher(commandWithOffsets);
        while (offsetMatcher.find()) {
            String time = offsetMatcher.group(2).trim();
            long value = TimeUtils.toMilliSeconds(time);
            offsets.add(offsetMatcher.group(1).equals("+") ? value : -value);
        }

        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Date date;
                if ("now".equals(command)) {
                    date = new Date();
                } else if ("exchangeCreated".equals(command)) {
                    long num = exchange.getCreated();
                    date = new Date(num);
                } else if (command.startsWith("header.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    Object obj = exchange.getMessage().getHeader(key);
                    if (obj instanceof Date) {
                        date = (Date) obj;
                    } else if (obj instanceof Long) {
                        date = new Date((Long) obj);
                    } else {
                        throw new IllegalArgumentException("Cannot find Date/long object at command: " + command);
                    }
                } else if (command.startsWith("exchangeProperty.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    Object obj = exchange.getProperty(key);
                    if (obj instanceof Date) {
                        date = (Date) obj;
                    } else if (obj instanceof Long) {
                        date = new Date((Long) obj);
                    } else {
                        throw new IllegalArgumentException("Cannot find Date/long object at command: " + command);
                    }
                } else if ("file".equals(command)) {
                    Long num = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
                    if (num != null && num > 0) {
                        date = new Date(num);
                    } else {
                        date = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Date.class);
                        if (date == null) {
                            throw new IllegalArgumentException(
                                    "Cannot find " + Exchange.FILE_LAST_MODIFIED + " header at command: " + command);
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Command not supported for dateExpression: " + command);
                }

                // Apply offsets
                long dateAsLong = date.getTime();
                for (long offset : offsets) {
                    dateAsLong += offset;
                }
                date = new Date(dateAsLong);

                if (pattern != null && !pattern.isEmpty()) {
                    SimpleDateFormat df = new SimpleDateFormat(pattern);
                    if (timezone != null && !timezone.isEmpty()) {
                        df.setTimeZone(TimeZone.getTimeZone(timezone));
                    }
                    return df.format(date);
                } else {
                    return date;
                }
            }

            @Override
            public String toString() {
                if (timezone != null && pattern != null) {
                    return "date(" + commandWithOffsets + ":" + timezone + ":" + pattern + ")";
                } else if (pattern != null) {
                    return "date(" + commandWithOffsets + ":" + pattern + ")";
                } else {
                    return "date(" + commandWithOffsets + ")";
                }
            }
        };
    }

    public static Expression skipIteratorExpression(final Expression expression, final int skip) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it,
                        "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                return new SkipIterator(it, skip);
            }

            @Override
            public String toString() {
                return "skip " + expression + " " + skip + " times";
            }
        };
    }

    /**
     * Returns the expression for the {@code null} value
     */
    public static Expression nullExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return null;
            }

            @Override
            public String toString() {
                return "null";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type and invoking methods on
     * the converted body defined in a simple OGNL notation
     */
    public static Expression mandatoryBodyOgnlExpression(final String name, final String ognl) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;
            private Language bean;

            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Object body;
                try {
                    body = exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Expression ognlExp = bean.createExpression(null, new Object[] { body, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + name + "](" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type
     */
    public static Expression mandatoryBodyExpression(final String name) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;

            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + name + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type and invoking methods on
     * the converted body defined in a simple OGNL notation
     */
    public static Expression bodyOgnlExpression(final String name, final String ognl) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;
            private Language bean;

            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Object body = exchange.getIn().getBody(type);
                if (body != null) {
                    // ognl is able to evaluate method name if it contains nested functions
                    // so we should not eager evaluate ognl as a string
                    Expression ognlExp = bean.createExpression(null, new Object[] { body, ognl });
                    ognlExp.init(exchange.getContext());
                    return ognlExp.evaluate(exchange, Object.class);
                } else {
                    return null;
                }
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "bodyOgnlAs[" + name + "](" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchange invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the exchange in a simple OGNL syntax
     */
    public static Expression exchangeOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Language bean;

            public Object evaluate(Exchange exchange) {
                // ognl is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate ognl as a string
                Expression ognlExp = bean.createExpression(null, new Object[] { exchange, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "exchangeOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges camelContext invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the context in a simple OGNL syntax
     */
    public static Expression camelContextOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = ExpressionBuilder.beanExpression(context, ognl);
                exp.init(context);
            }

            public Object evaluate(Exchange exchange) {
                // ognl is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate ognl as a string
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "camelContextOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression bodyOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Language bean;

            public Object evaluate(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                if (body == null) {
                    return null;
                }
                Expression ognlExp = bean.createExpression(null, new Object[] { body, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "bodyOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns an expression that caches the evaluation of another expression and returns the cached value, to avoid
     * re-evaluating the expression.
     *
     * @param  expression the target expression to cache
     * @return            the cached value
     */
    public static Expression cacheExpression(final Expression expression) {
        return new ExpressionAdapter() {
            private final AtomicReference<Object> cache = new AtomicReference<>();

            public Object evaluate(Exchange exchange) {
                Object answer = cache.get();
                if (answer == null) {
                    answer = expression.evaluate(exchange, Object.class);
                    cache.set(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return expression.toString();
            }
        };
    }

    /**
     * Returns an expression for a type value
     *
     * @param  name the type name
     * @return      an expression object which will return the type value
     */
    public static Expression typeExpression(final String name) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
            }

            public Object evaluate(Exchange exchange) {
                // it may refer to a class type
                String text = exp.evaluate(exchange, String.class);
                Class<?> type = classResolver.resolveClass(text);
                if (type != null) {
                    return type;
                }

                int pos = text.lastIndexOf('.');
                if (pos > 0) {
                    String before = text.substring(0, pos);
                    String after = text.substring(pos + 1);
                    type = classResolver.resolveClass(before);
                    if (type != null) {
                        return ObjectHelper.lookupConstantFieldValue(type, after);
                    }
                }

                throw CamelExecutionException.wrapCamelExecutionException(exchange,
                        new ClassNotFoundException("Cannot find type " + text));
            }

            @Override
            public String toString() {
                return "type:" + name;
            }
        };
    }

    /**
     * Returns an expression for the property value of exchange with the given name invoking methods defined in a simple
     * OGNL notation
     *
     * @param ognl methods to invoke on the property in a simple OGNL syntax
     */
    public static Expression propertyOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(
                ognl, "propertyOgnl(" + ognl + ")",
                (exchange, exp) -> {
                    String text = exp.evaluate(exchange, String.class);
                    return exchange.getProperty(text);
                });
    }

    /**
     * Returns the expression for the exchanges exception invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression exchangeExceptionOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Language bean;

            public Object evaluate(Exchange exchange) {
                Object exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }

                if (exception == null) {
                    return null;
                }

                // ognl is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate ognl as a string
                Expression ognlExp = bean.createExpression(null, new Object[] { exception, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "exchangeExceptionOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Expression adapter for OGNL expression from Message Header or Exchange property
     */
    public static class KeyedOgnlExpressionAdapter extends ExpressionAdapter {
        private final String ognl;
        private final String toStringValue;
        private final KeyedEntityRetrievalStrategy keyedEntityRetrievalStrategy;
        private String key;
        private String keySuffix;
        private String method;
        private Expression keyExpression;
        private Expression ognlExpression;
        private Language beanLanguage;

        KeyedOgnlExpressionAdapter(String ognl, String toStringValue,
                                   KeyedEntityRetrievalStrategy keyedEntityRetrievalStrategy) {
            this.ognl = ognl;
            this.toStringValue = toStringValue;
            this.keyedEntityRetrievalStrategy = keyedEntityRetrievalStrategy;

            // Split ognl except when this is not a Map, Array
            // and we would like to keep the dots within the key name
            List<String> methods = OgnlHelper.splitOgnl(ognl);

            key = methods.get(0);
            keySuffix = "";
            // if ognl starts with a key inside brackets (eg: [foo.bar])
            // remove starting and ending brackets from key
            if (key.startsWith("[") && key.endsWith("]")) {
                key = StringHelper.removeLeadingAndEndingQuotes(key.substring(1, key.length() - 1));
                keySuffix = StringHelper.after(methods.get(0), key);
            }
            // remove any OGNL operators so we got the pure key name
            key = OgnlHelper.removeOperators(key);
            // and this may be the last remainder method to try as OGNL if there are no exchange properties with those key names
            method = StringHelper.after(ognl, key + keySuffix);
        }

        @Override
        public void init(CamelContext context) {
            beanLanguage = context.resolveLanguage("bean");
            ognlExpression = ExpressionBuilder.simpleExpression(ognl);
            ognlExpression.init(context);
            // key must be lazy eval as it only used in special situations
        }

        @Override
        public Object evaluate(Exchange exchange) {
            // try with full name first
            Object property = keyedEntityRetrievalStrategy.getKeyedEntity(exchange, ognlExpression);
            if (property != null) {
                return property;
            }

            // key must be lazy eval as it only used in special situations
            if (keyExpression == null) {
                keyExpression = ExpressionBuilder.simpleExpression(key);
                keyExpression.init(exchange.getContext());
            }

            property = keyedEntityRetrievalStrategy.getKeyedEntity(exchange, keyExpression);
            if (property == null) {
                return null;
            }
            if (method != null) {
                Expression exp = beanLanguage.createExpression(null, new Object[] { property, method });
                exp.init(exchange.getContext());
                return exp.evaluate(exchange, Object.class);
            } else {
                return property;
            }
        }

        @Override
        public String toString() {
            return toStringValue;
        }

        /**
         * Strategy to retrieve the value based on the key
         */
        public interface KeyedEntityRetrievalStrategy {
            Object getKeyedEntity(Exchange exchange, Expression key);
        }
    }
}
