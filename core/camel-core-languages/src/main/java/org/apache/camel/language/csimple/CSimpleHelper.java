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
package org.apache.camel.language.csimple;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.GroupIterator;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.InetAddressUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.SkipIterator;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;

/**
 * A set of helper as static imports for the Camel compiled simple language.
 */
public final class CSimpleHelper {

    // this is special for the range operator where you define the range as from..to (where from and to are numbers)
    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(\\.\\.)(\\d+)$");
    // use for date operator
    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])([^+-]+)");

    private static ExchangeFormatter exchangeFormatter;
    private static Language beanLanguage;

    private CSimpleHelper() {
    }

    public static <T> T bodyAs(Message message, Class<T> type) {
        return message.getBody(type);
    }

    public static <T> T mandatoryBodyAs(Message message, Class<T> type) throws InvalidPayloadException {
        return message.getMandatoryBody(type);
    }

    public static <T> T bodyAsIndex(Message message, Class<T> type, int key) {
        return bodyAsIndex(message, type, "" + key);
    }

    public static <T> T bodyAsIndex(Message message, Class<T> type, String key) {
        Object obj = message.getBody();
        // try key as-is as it may be using dots or something that valid
        Object objKey = doObjectAsIndex(message.getExchange().getContext(), obj, key);
        if (objKey != null && objKey != obj) {
            return type.cast(objKey);
        }
        // the key may contain multiple keys ([0][foo]) so we need to walk these keys
        List<String> keys = OgnlHelper.splitOgnl(key);
        for (String k : keys) {
            if (k.startsWith("[") && k.endsWith("]")) {
                k = StringHelper.between(k, "[", "]");
            }
            obj = doObjectAsIndex(message.getExchange().getContext(), obj, k);
        }
        return type.cast(obj);
    }

    public static <T> T mandatoryBodyAsIndex(Message message, Class<T> type, int key) throws InvalidPayloadException {
        T out = bodyAsIndex(message, type, "" + key);
        if (out == null) {
            throw new InvalidPayloadException(message.getExchange(), type, message);
        }
        return out;
    }

    public static <T> T mandatoryBodyAsIndex(Message message, Class<T> type, String key) throws InvalidPayloadException {
        T out = bodyAsIndex(message, type, key);
        if (out == null) {
            throw new InvalidPayloadException(message.getExchange(), type, message);
        }
        return out;
    }

    public static Object header(Message message, String name) {
        return message.getHeader(name);
    }

    public static <T> T headerAs(Message message, String name, Class<T> type) {
        return message.getHeader(name, type);
    }

    public static <T> T headerAsIndex(Message message, Class<T> type, String name, String key) {
        Object obj = message.getHeader(name);
        // try key as-is as it may be using dots or something that valid
        Object objKey = doObjectAsIndex(message.getExchange().getContext(), obj, key);
        if (objKey != null && objKey != obj) {
            return type.cast(objKey);
        }
        // the key may contain multiple keys ([0][foo]) so we need to walk these keys
        List<String> keys = OgnlHelper.splitOgnl(key);
        for (String k : keys) {
            if (k.startsWith("[") && k.endsWith("]")) {
                k = StringHelper.between(k, "[", "]");
            }
            obj = doObjectAsIndex(message.getExchange().getContext(), obj, k);
        }
        return type.cast(obj);
    }

    public static Object exchangeProperty(Exchange exchange, String name) {
        return exchange.getProperty(name);
    }

    public static <T> T exchangePropertyAs(Exchange exchange, String name, Class<T> type) {
        return exchange.getProperty(name, type);
    }

    public static <T> T exchangePropertyAsIndex(Exchange exchange, Class<T> type, String name, String key) {
        Object obj = exchange.getProperty(name);
        // try key as-is as it may be using dots or something that valid
        Object objKey = doObjectAsIndex(exchange.getContext(), obj, key);
        if (objKey != null && objKey != obj) {
            return type.cast(objKey);
        }
        // the key may contain multiple keys ([0][foo]) so we need to walk these keys
        List<String> keys = OgnlHelper.splitOgnl(key);
        for (String k : keys) {
            if (k.startsWith("[") && k.endsWith("]")) {
                k = StringHelper.between(k, "[", "]");
            }
            obj = doObjectAsIndex(exchange.getContext(), obj, k);
        }
        return type.cast(obj);
    }

    public static String bodyOneLine(Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);
        if (body == null) {
            return null;
        }
        body = StringHelper.replaceAll(body, System.lineSeparator(), "");
        return body;
    }

    public static Exception exception(Exchange exchange) {
        Exception exception = exchange.getException();
        if (exception == null) {
            exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }
        return exception;
    }

    public static <T> T exceptionAs(Exchange exchange, Class<T> type) {
        Exception exception = exchange.getException();
        if (exception == null) {
            exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }
        if (exception != null) {
            return type.cast(exception);
        } else {
            return null;
        }
    }

    public static String exceptionMessage(Exchange exchange) {
        Exception exception = exception(exchange);
        if (exception != null) {
            return exception.getMessage();
        } else {
            return null;
        }
    }

    public static String exceptionStacktrace(Exchange exchange) {
        Exception exception = exception(exchange);
        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            IOHelper.close(pw, sw);
            return sw.toString();
        } else {
            return null;
        }
    }

    public static String threadName() {
        return Thread.currentThread().getName();
    }

    public static String hostName() {
        return InetAddressUtil.getLocalHostNameSafe();
    }

    public static String routeId(Exchange exchange) {
        return ExchangeHelper.getRouteId(exchange);
    }

    public static String stepId(Exchange exchange) {
        return exchange.getProperty(Exchange.STEP_ID, String.class);
    }

    public static String fileName(Message message) {
        return message.getHeader(Exchange.FILE_NAME, String.class);
    }

    public static String fileNameNoExt(Message message) {
        String name = message.getHeader(Exchange.FILE_NAME, String.class);
        return FileUtil.stripExt(name);
    }

    public static String fileNameNoExtSingle(Message message) {
        String name = message.getHeader(Exchange.FILE_NAME, String.class);
        return FileUtil.stripExt(name, true);
    }

    public static String fileNameExt(Message message) {
        String name = message.getHeader(Exchange.FILE_NAME, String.class);
        return FileUtil.onlyExt(name);
    }

    public static String fileNameExtSingle(Message message) {
        String name = message.getHeader(Exchange.FILE_NAME, String.class);
        return FileUtil.onlyExt(name, true);
    }

    public static String fileOnlyName(Message message) {
        String answer = message.getHeader(Exchange.FILE_NAME_ONLY, String.class);
        if (answer == null) {
            answer = message.getHeader(Exchange.FILE_NAME, String.class);
            answer = FileUtil.stripPath(answer);
        }
        return answer;
    }

    public static String fileOnlyNameNoExt(Message message) {
        String name = fileOnlyName(message);
        return FileUtil.stripExt(name);
    }

    public static String fileOnlyNameNoExtSingle(Message message) {
        String name = fileOnlyName(message);
        return FileUtil.stripExt(name, true);
    }

    public static String fileParent(Message message) {
        return message.getHeader("CamelFileParent", String.class);
    }

    public static String filePath(Message message) {
        return message.getHeader("CamelFilePath", String.class);
    }

    public static Boolean fileAbsolute(Message message) {
        return message.getHeader("CamelFileAbsolute", Boolean.class);
    }

    public static String fileAbsolutePath(Message message) {
        return message.getHeader("CamelFileAbsolutePath", String.class);
    }

    public static Long fileSize(Message message) {
        return message.getHeader(Exchange.FILE_LENGTH, Long.class);
    }

    public static Long fileModified(Message message) {
        return message.getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
    }

    public static Date date(Exchange exchange, String commandWithOffsets) {
        return (Date) doDate(exchange, commandWithOffsets, null, null);
    }

    public static String date(Exchange exchange, String commandWithOffsets, String timezone, String pattern) {
        return (String) doDate(exchange, commandWithOffsets, timezone, pattern);
    }

    private static Object doDate(Exchange exchange, String commandWithOffsets, String timezone, String pattern) {
        final String command = commandWithOffsets.split("[+-]", 2)[0].trim();
        // Capture optional time offsets
        final List<Long> offsets = new ArrayList<>();
        Matcher offsetMatcher = OFFSET_PATTERN.matcher(commandWithOffsets);
        while (offsetMatcher.find()) {
            String time = offsetMatcher.group(2).trim();
            long value = TimeUtils.toMilliSeconds(time);
            offsets.add(offsetMatcher.group(1).equals("+") ? value : -value);
        }

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

    public static String property(Exchange exchange, String key, String defaultValue) {
        try {
            // enclose key with {{ }} to force parsing as key can be a nested expression too
            PropertiesComponent pc = exchange.getContext().getPropertiesComponent();
            return pc.parseUri(PropertiesComponent.PREFIX_TOKEN + key + PropertiesComponent.SUFFIX_TOKEN);
        } catch (Exception e) {
            // property with key not found, use default value if provided
            if (defaultValue != null) {
                return defaultValue;
            }
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public static Object ref(Exchange exchange, String key) {
        return exchange.getContext().getRegistry().lookupByName(key);
    }

    public static Class<?> type(Exchange exchange, Class<?> type) {
        return type;
    }

    public static Object type(Exchange exchange, Class<?> type, String field) {
        return ObjectHelper.lookupConstantFieldValue(type, field);
    }

    public static Object bean(Exchange exchange, String ref, String method, Object scope) {
        Class<?> type = null;
        if (ref != null && ref.startsWith("type:")) {
            try {
                type = exchange.getContext().getClassResolver().resolveMandatoryClass(ref.substring(5));
                ref = null;
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        Language bean = getOrCreateBeanLanguage(exchange.getContext());
        Object[] properties = new Object[5];
        properties[2] = type;
        properties[3] = ref;
        properties[1] = method;
        properties[4] = scope;
        Expression exp = bean.createExpression(null, properties);
        exp.init(exchange.getContext());
        return exp.evaluate(exchange, Object.class);
    }

    private static Language getOrCreateBeanLanguage(CamelContext camelContext) {
        if (beanLanguage == null) {
            beanLanguage = camelContext.resolveLanguage("bean");
        }
        return beanLanguage;
    }

    public static Object increment(Exchange exchange, Object number) {
        Number num = exchange.getContext().getTypeConverter().tryConvertTo(Number.class, exchange, number);
        if (num instanceof Integer) {
            int val = num.intValue();
            val++;
            return val;
        } else if (num instanceof Long) {
            long val = num.longValue();
            val++;
            return val;
        } else {
            // cannot convert the expression as a number
            Exception cause = new CamelExchangeException("Cannot evaluate message body as a number", exchange);
            throw RuntimeCamelException.wrapRuntimeCamelException(cause);
        }
    }

    public static Object decrement(Exchange exchange, Object number) {
        Number num = exchange.getContext().getTypeConverter().tryConvertTo(Number.class, exchange, number);
        if (num instanceof Integer) {
            int val = num.intValue();
            val--;
            return val;
        } else if (num instanceof Long) {
            long val = num.longValue();
            val--;
            return val;
        } else {
            // cannot convert the expression as a number
            Exception cause = new CamelExchangeException("Cannot evaluate message body as a number", exchange);
            throw RuntimeCamelException.wrapRuntimeCamelException(cause);
        }
    }

    public static int random(Exchange exchange, Object min, Object max) {
        int num1 = exchange.getContext().getTypeConverter().tryConvertTo(int.class, exchange, min);
        int num2 = exchange.getContext().getTypeConverter().tryConvertTo(int.class, exchange, max);
        Random random = new Random();
        return random.nextInt(num2 - num1) + num1;
    }

    public static SkipIterator skip(Exchange exchange, Object skip) {
        int num = exchange.getContext().getTypeConverter().tryConvertTo(int.class, exchange, skip);
        Iterator<?> it = org.apache.camel.support.ObjectHelper.createIterator(exchange.getMessage().getBody());
        return new SkipIterator(it, num);
    }

    public static GroupIterator collate(Exchange exchange, Object group) {
        int num = exchange.getContext().getTypeConverter().tryConvertTo(int.class, exchange, group);
        Iterator<?> it = org.apache.camel.support.ObjectHelper.createIterator(exchange.getMessage().getBody());
        return new GroupIterator(exchange, it, num);
    }

    public static String messageHistory(Exchange exchange, boolean detailed) {
        ExchangeFormatter formatter = getOrCreateExchangeFormatter(exchange.getContext());
        return MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, detailed);
    }

    public static String sys(String name) {
        return System.getProperty(name);
    }

    public static String sysenv(String name) {
        String answer = null;
        if (name != null) {
            // lookup OS env with upper case key
            name = name.toUpperCase();
            answer = System.getenv(name);
            // some OS do not support dashes in keys, so replace with underscore
            if (answer == null) {
                String noDashKey = name.replace('-', '_');
                answer = System.getenv(noDashKey);
            }
        }
        return answer;
    }

    private static ExchangeFormatter getOrCreateExchangeFormatter(CamelContext camelContext) {
        if (exchangeFormatter == null) {
            Set<ExchangeFormatter> formatters = camelContext.getRegistry().findByType(ExchangeFormatter.class);
            if (formatters != null && formatters.size() == 1) {
                exchangeFormatter = formatters.iterator().next();
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
                exchangeFormatter = def;
            }
        }
        return exchangeFormatter;
    }

    public static boolean isEqualTo(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceEquals(exchange.getContext().getTypeConverter(), leftValue,
                rightValue);
    }

    public static boolean isEqualToIgnoreCase(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceEquals(exchange.getContext().getTypeConverter(), leftValue,
                rightValue, true);
    }

    public static boolean isNotEqualTo(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceNotEquals(exchange.getContext().getTypeConverter(), leftValue,
                rightValue);
    }

    public static boolean isGreaterThan(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue,
                rightValue)
               > 0;
    }

    public static boolean isGreaterThanOrEqualTo(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue,
                rightValue)
               >= 0;
    }

    public static boolean isLessThan(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue,
                rightValue)
               < 0;
    }

    public static boolean isLessThanOrEqualTo(Exchange exchange, Object leftValue, Object rightValue) {
        return org.apache.camel.support.ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue,
                rightValue)
               <= 0;
    }

    public static boolean contains(Exchange exchange, Object leftValue, Object rightValue) {
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }
        return org.apache.camel.support.ObjectHelper.typeCoerceContains(exchange.getContext().getTypeConverter(), leftValue,
                rightValue, false);
    }

    public static boolean containsIgnoreCase(Exchange exchange, Object leftValue, Object rightValue) {
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }
        return org.apache.camel.support.ObjectHelper.typeCoerceContains(exchange.getContext().getTypeConverter(), leftValue,
                rightValue, true);
    }

    public static boolean regexp(Exchange exchange, Object leftValue, Object rightValue) {
        String text = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, leftValue);
        String pattern = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, rightValue);
        return text.matches(pattern);
    }

    public static boolean in(Exchange exchange, Object leftValue, Object rightValue) {
        // okay the in operator is a bit more complex as we need to build a list of values
        // from the right hand side expression.
        // each element on the right hand side must be separated by comma (default for create iterator)
        Iterator<?> it = org.apache.camel.support.ObjectHelper.createIterator(rightValue);
        List<Object> values = new ArrayList<>();
        while (it.hasNext()) {
            values.add(it.next());
        }
        for (Object value : values) {
            if (isEqualTo(exchange, leftValue, value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean range(Exchange exchange, Object leftValue, Object rightValue) {
        String range = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, rightValue);
        Matcher matcher = RANGE_PATTERN.matcher(range);
        if (matcher.matches()) {
            // wrap as constant expression for the from and to values
            String from = matcher.group(1);
            String to = matcher.group(3);

            // build a compound predicate for the range
            return isGreaterThanOrEqualTo(exchange, leftValue, from) && isLessThanOrEqualTo(exchange, leftValue, to);
        } else {
            throw new IllegalArgumentException(
                    "Range operator is not valid. Valid syntax:'from..to' (where from and to are numbers).");
        }
    }

    public static boolean startsWith(Exchange exchange, Object leftValue, Object rightValue) {
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }
        String leftStr = exchange.getContext().getTypeConverter().convertTo(String.class, leftValue);
        String rightStr = exchange.getContext().getTypeConverter().convertTo(String.class, rightValue);
        if (leftStr != null && rightStr != null) {
            return leftStr.startsWith(rightStr);
        } else {
            return false;
        }
    }

    public static boolean endsWith(Exchange exchange, Object leftValue, Object rightValue) {
        if (leftValue == null && rightValue == null) {
            // they are equal
            return true;
        } else if (leftValue == null || rightValue == null) {
            // only one of them is null so they are not equal
            return false;
        }
        String leftStr = exchange.getContext().getTypeConverter().convertTo(String.class, leftValue);
        String rightStr = exchange.getContext().getTypeConverter().convertTo(String.class, rightValue);
        if (leftStr != null && rightStr != null) {
            return leftStr.endsWith(rightStr);
        } else {
            return false;
        }
    }

    public static boolean is(Exchange exchange, Object leftValue, Class<?> type) {
        return type.isInstance(leftValue);
    }

    private static Object doObjectAsIndex(CamelContext context, Object obj, String key) {
        if (obj != null && obj.getClass().isArray()) {
            int size = Array.getLength(obj);
            Integer num = indexAsNumber(context, key, size);
            if (num != null && num >= 0 && size > 0 && size > num - 1) {
                obj = Array.get(obj, num);
            }
        } else if (obj instanceof List) {
            List list = (List) obj;
            Integer num = indexAsNumber(context, key, list.size());
            if (num != null && num >= 0 && !list.isEmpty() && list.size() > num - 1) {
                obj = list.get(num);
            }
        } else if (obj instanceof Map) {
            Map map = (Map) obj;
            obj = map.get(key);
        } else {
            // object not a collection type
            return null;
        }
        return obj;
    }

    private static Integer indexAsNumber(CamelContext context, String key, int size) {
        Integer num;
        if (key.startsWith("last")) {
            num = size - 1;

            // maybe its an expression to subtract a number after last
            String after = StringHelper.after(key, "-");
            if (after != null) {
                Integer redux
                        = context.getTypeConverter().tryConvertTo(Integer.class, after.trim());
                if (redux != null) {
                    num -= redux;
                } else {
                    throw new ExpressionIllegalSyntaxException(key);
                }
            }
        } else {
            num = context.getTypeConverter().tryConvertTo(Integer.class, key);
        }
        return num;
    }

}
