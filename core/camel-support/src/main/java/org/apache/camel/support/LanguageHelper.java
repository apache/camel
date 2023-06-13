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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
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
}
