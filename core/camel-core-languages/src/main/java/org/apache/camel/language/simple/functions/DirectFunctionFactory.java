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
package org.apache.camel.language.simple.functions;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;

/**
 * Built-in Simple keyword functions that are matched by exact name (no prefix): {@code ${id}},
 * {@code ${messageTimestamp}}, {@code ${exchangeId}}, {@code ${exchange}}, {@code ${logExchange}},
 * {@code ${exception}}, {@code ${exception.message}}, {@code ${exception.stacktrace}}, {@code ${threadId}},
 * {@code ${threadName}}, {@code ${hostname}}, {@code ${camelId}}, {@code ${routeId}}, {@code ${fromRouteId}},
 * {@code ${routeGroup}}, {@code ${stepId}}, {@code ${null}}.
 *
 * <p>
 * These functions must be dispatched <em>before</em> the inline prefix checks in {@code SimpleFunctionExpression}
 * (e.g., the {@code exception} OGNL check and the {@code exchange} OGNL check), because names like {@code exchangeId}
 * and {@code exception.message} share a prefix with those checks.
 */
public final class DirectFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        if (ObjectHelper.equal(function, "id")) {
            return ExpressionBuilder.messageIdExpression();
        } else if (ObjectHelper.equal(function, "messageTimestamp")) {
            return ExpressionBuilder.messageTimestampExpression();
        } else if (ObjectHelper.equal(function, "exchangeId")) {
            return ExpressionBuilder.exchangeIdExpression();
        } else if (ObjectHelper.equal(function, "exchange")) {
            return ExpressionBuilder.exchangeExpression();
        } else if (ObjectHelper.equal(function, "logExchange")) {
            return ExpressionBuilder.logExchange();
        } else if (ObjectHelper.equal(function, "exception")) {
            return ExpressionBuilder.exchangeExceptionExpression();
        } else if (ObjectHelper.equal(function, "exception.message")) {
            return ExpressionBuilder.exchangeExceptionMessageExpression();
        } else if (ObjectHelper.equal(function, "exception.stacktrace")) {
            return ExpressionBuilder.exchangeExceptionStackTraceExpression();
        } else if (ObjectHelper.equal(function, "threadId")) {
            return ExpressionBuilder.threadIdExpression();
        } else if (ObjectHelper.equal(function, "threadName")) {
            return ExpressionBuilder.threadNameExpression();
        } else if (ObjectHelper.equal(function, "hostname")) {
            return ExpressionBuilder.hostnameExpression();
        } else if (ObjectHelper.equal(function, "camelId")) {
            return ExpressionBuilder.camelContextNameExpression();
        } else if (ObjectHelper.equal(function, "routeId")) {
            return ExpressionBuilder.routeIdExpression();
        } else if (ObjectHelper.equal(function, "fromRouteId")) {
            return ExpressionBuilder.fromRouteIdExpression();
        } else if (ObjectHelper.equal(function, "routeGroup")) {
            return ExpressionBuilder.routeGroupExpression();
        } else if (ObjectHelper.equal(function, "stepId")) {
            return ExpressionBuilder.stepIdExpression();
        } else if (ObjectHelper.equal(function, "null")) {
            return SimpleExpressionBuilder.nullExpression();
        }

        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        if (ObjectHelper.equal(function, "id")) {
            return "message.getMessageId()";
        } else if (ObjectHelper.equal(function, "messageTimestamp")) {
            return "message.getMessageTimestamp()";
        } else if (ObjectHelper.equal(function, "exchangeId")) {
            return "exchange.getExchangeId()";
        } else if (ObjectHelper.equal(function, "exchange")) {
            return "exchange";
        } else if (ObjectHelper.equal(function, "logExchange")) {
            return "logExchange(exchange)";
        } else if (ObjectHelper.equal(function, "exception")) {
            return "exception(exchange)";
        } else if (ObjectHelper.equal(function, "exception.message")) {
            return "exceptionMessage(exchange)";
        } else if (ObjectHelper.equal(function, "exception.stacktrace")) {
            return "exceptionStacktrace(exchange)";
        } else if (ObjectHelper.equal(function, "threadId")) {
            return "threadId()";
        } else if (ObjectHelper.equal(function, "threadName")) {
            return "threadName()";
        } else if (ObjectHelper.equal(function, "hostname")) {
            return "hostName()";
        } else if (ObjectHelper.equal(function, "camelId")) {
            return "context.getName()";
        } else if (ObjectHelper.equal(function, "fromRouteId")) {
            return "fromRouteId(exchange)";
        } else if (ObjectHelper.equal(function, "routeId")) {
            return "routeId(exchange)";
        } else if (ObjectHelper.equal(function, "stepId")) {
            return "stepId(exchange)";
        } else if (ObjectHelper.equal(function, "null")) {
            return "null";
        }

        return null;
    }
}
