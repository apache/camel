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

package org.apache.camel.support;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.util.IOHelper;

public final class LanguageHelper {
    private LanguageHelper() {

    }

    /**
     * Extracts the exception from an Exchange
     *
     * @param  exchange the exchange to extract the exception
     * @return          the exception or null if not present
     */
    public static Exception exception(Exchange exchange) {
        Exception exception = exchange.getException();
        if (exception == null) {
            exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
        }
        return exception;
    }

    /**
     * Extracts the exception message from an exchange
     *
     * @param  exchange the exchange to extract the exception
     * @return          the message or null if not found
     */
    public static String exceptionMessage(Exchange exchange) {
        Exception exception = exception(exchange);
        if (exception != null) {
            return exception.getMessage();
        } else {
            return null;
        }
    }

    /**
     * Gets the exception stack trace from an exchange
     *
     * @param  exchange exchange the exchange to extract the stack trace
     * @return          the stack trace or null if no exception is present on the exchange
     */
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

    /**
     * Tests if the exchange ends with a given value
     *
     * @param  exchange   the exchange to test
     * @param  leftValue  the value being tested
     * @param  rightValue the suffix to test
     * @return            true if it ends with or false otherwise
     */
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

    /**
     * Tests if the exchange starts with a given value
     *
     * @param  exchange   the exchange to test
     * @param  leftValue  the value being tested
     * @param  rightValue the prefix to test
     * @return            true if it starts with or false otherwise
     */
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

    /**
     * Gets the exchange formatter or creates one if unset
     *
     * @param  camelContext      the Camel context
     * @param  exchangeFormatter the exchange formatter
     * @return                   the exchange formatter or the newly created one if previously unset
     */
    public static ExchangeFormatter getOrCreateExchangeFormatter(
            CamelContext camelContext, ExchangeFormatter exchangeFormatter) {
        if (exchangeFormatter == null) {
            exchangeFormatter = camelContext.getRegistry().findSingleByType(ExchangeFormatter.class);
            if (exchangeFormatter == null) {
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
}
