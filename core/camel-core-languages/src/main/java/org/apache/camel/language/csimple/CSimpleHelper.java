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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.GroupIterator;
import org.apache.camel.support.LanguageHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.InetAddressUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.SkipIterator;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.Jsoner;
import org.apache.camel.util.xml.pretty.XmlPrettyPrinter;

/**
 * A set of helper as static imports for the Camel compiled simple language.
 */
public final class CSimpleHelper {

    // this is special for the range operator where you define the range as from..to (where from and to are numbers)
    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(\\.\\.)(\\d+)$");
    // use for date operator
    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])([^+-]+)");

    private static ExchangeFormatter exchangeFormatter;

    private CSimpleHelper() {
    }

    public static <T> T messageAs(Exchange exchange, Class<T> type) {
        return exchange.getMessage(type);
    }

    public static <T> T bodyAs(Message message, Class<T> type) {
        return message.getBody(type);
    }

    public static <T> T mandatoryBodyAs(Message message, Class<T> type) throws InvalidPayloadException {
        return message.getMandatoryBody(type);
    }

    public static <T> T bodyAsIndex(Message message, Class<T> type, int key) {
        return bodyAsIndex(message, type, Integer.toString(key));
    }

    public static <T> T bodyAsIndex(Message message, Class<T> type, String key) {
        final Object obj = message.getBody();
        // try key as-is as it may be using dots or something that valid
        return tryCast(message.getExchange().getContext(), type, key, obj);
    }

    public static <T> T mandatoryBodyAsIndex(Message message, Class<T> type, int key) throws InvalidPayloadException {
        T out = bodyAsIndex(message, type, Integer.toString(key));
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
        final Object obj = message.getHeader(name);
        // try key as-is as it may be using dots or something that valid
        return tryCast(message.getExchange().getContext(), type, key, obj);
    }

    private static <T> T tryCast(CamelContext context, Class<T> type, String key, Object obj) {
        final Object objKey = doObjectAsIndex(context, obj, key);
        if (objKey != null && objKey != obj) {
            return type.cast(objKey);
        }
        // the key may contain multiple keys ([0][foo]) so we need to walk these keys
        List<String> keys = OgnlHelper.splitOgnl(key);
        for (String k : keys) {
            if (k.startsWith("[") && k.endsWith("]")) {
                k = StringHelper.between(k, "[", "]");
            }
            obj = doObjectAsIndex(context, obj, k);
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
        body = body.replace(System.lineSeparator(), "");
        return body;
    }

    public static String prettyBody(Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);

        if (body == null) {
            return null;
        } else if (body.startsWith("{") && body.endsWith("}") || body.startsWith("[") && body.endsWith("]")) {
            body = Jsoner.prettyPrint(body.trim()); //json
        } else if (body.startsWith("<") && body.endsWith(">")) {
            return CSimpleHelper.prettyXml(body.trim()); //xml
        }

        return body;
    }

    private static String prettyXml(String rawXml) {
        try {
            boolean includeDeclaration = rawXml.startsWith("<?xml");
            return XmlPrettyPrinter.pettyPrint(rawXml, 2, includeDeclaration);
        } catch (Exception e) {
            return rawXml;
        }
    }

    public static Exception exception(Exchange exchange) {
        return LanguageHelper.exception(exchange);
    }

    public static <T> T exceptionAs(Exchange exchange, Class<T> type) {
        Exception exception = exchange.getException();
        if (exception == null) {
            exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
        }
        if (exception != null) {
            return type.cast(exception);
        } else {
            return null;
        }
    }

    public static String exceptionMessage(Exchange exchange) {
        return LanguageHelper.exceptionMessage(exchange);
    }

    public static String exceptionStacktrace(Exchange exchange) {
        return LanguageHelper.exceptionStacktrace(exchange);
    }

    public static String threadName() {
        return Thread.currentThread().getName();
    }

    public static long threadId() {
        return Thread.currentThread().getId();
    }

    public static String hostName() {
        return InetAddressUtil.getLocalHostNameSafe();
    }

    public static String routeId(Exchange exchange) {
        return ExchangeHelper.getRouteId(exchange);
    }

    public static String stepId(Exchange exchange) {
        return exchange.getProperty(ExchangePropertyKey.STEP_ID, String.class);
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
        final List<Long> offsets = LanguageHelper.captureOffsets(commandWithOffsets, OFFSET_PATTERN);

        Date date = evalDate(exchange, command);

        return LanguageHelper.applyDateOffsets(date, offsets, pattern, timezone);
    }

    private static Date evalDate(Exchange exchange, String command) {
        Date date;
        if ("now".equals(command)) {
            date = new Date();
        } else if ("exchangeCreated".equals(command)) {
            date = LanguageHelper.dateFromExchangeCreated(exchange);
        } else if (command.startsWith("header.")) {
            date = LanguageHelper.dateFromHeader(exchange, command, (e, o) -> failDueToMissingObjectAtCommand(command));
        } else if (command.startsWith("exchangeProperty.")) {
            date = LanguageHelper.dateFromExchangeProperty(exchange, command,
                    (e, o) -> failDueToMissingObjectAtCommand(command));
        } else if ("file".equals(command)) {
            date = LanguageHelper.dateFromFileLastModified(exchange, command);
        } else {
            throw new IllegalArgumentException("Command not supported for dateExpression: " + command);
        }
        return date;
    }

    private static Date failDueToMissingObjectAtCommand(String command) {
        throw new IllegalArgumentException("Cannot find Date/long object at command:" + command);
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

    public static Object bean(Exchange exchange, Language bean, String ref, String method, Object scope) {
        Class<?> type = null;
        if (ref != null && ref.startsWith("type:")) {
            try {
                type = exchange.getContext().getClassResolver().resolveMandatoryClass(ref.substring(5));
                ref = null;
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        Object[] properties = new Object[7];
        properties[3] = type;
        properties[4] = ref;
        properties[2] = method;
        properties[5] = scope;
        Expression exp = bean.createExpression(null, properties);
        exp.init(exchange.getContext());
        return exp.evaluate(exchange, Object.class);
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
        Random random = new Random(); // NOSONAR
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
        return new GroupIterator(it, num);
    }

    public static String messageHistory(Exchange exchange, boolean detailed) {
        ExchangeFormatter formatter = getOrCreateExchangeFormatter(exchange.getContext());
        return MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, detailed);
    }

    public static String sys(String name) {
        return System.getProperty(name);
    }

    public static String sysenv(String name) {
        return LanguageHelper.sysenv(name);
    }

    private static ExchangeFormatter getOrCreateExchangeFormatter(CamelContext camelContext) {
        return LanguageHelper.getOrCreateExchangeFormatter(camelContext, exchangeFormatter);
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
            // only one of them is null, so they are not equal
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
        return LanguageHelper.startsWith(exchange, leftValue, rightValue);
    }

    public static boolean endsWith(Exchange exchange, Object leftValue, Object rightValue) {
        return LanguageHelper.endsWith(exchange, leftValue, rightValue);
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
        } else if (obj instanceof List<?> list) {
            Integer num = indexAsNumber(context, key, list.size());
            if (num != null && num >= 0 && !list.isEmpty() && list.size() > num - 1) {
                obj = list.get(num);
            }
        } else if (obj instanceof Map<?, ?> map) {
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

            // maybe it's an expression to subtract a number after last
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
