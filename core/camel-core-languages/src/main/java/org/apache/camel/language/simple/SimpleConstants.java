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

import org.apache.camel.spi.Metadata;

public final class SimpleConstants {

    @Metadata(description = "The message body", label = "function")
    public static final String BODY = "body";
    @Metadata(description = "Converts the body to a String, and attempts to pretty print if JSon or XML; otherwise the body is returned as the String value.", javaType = "String", label = "function")
    public static final String PRETTY_BODY = "prettyBody";
    @Metadata(description = "Converts the body to a String and removes all line-breaks, so the string is in one line.", javaType = "String", label = "function")
    public static final String BODY_ONE_LINE = "bodyOneLine";
    @Metadata(description = "The original incoming body (only available if allowUseOriginalMessage=true).", label = "function")
    public static final String ORIGINAL_BODY = "originalBody";
    @Metadata(description = "The message id", javaType = "String", label = "function")
    public static final String ID = "id";
    @Metadata(description = "The message timestamp (millis since epoc) that this message originates from."
                            + " Some systems like JMS, Kafka, AWS have a timestamp on the event/message that Camel received. This method returns the timestamp if a timestamp exists."
                            + " The message timestamp and exchange created are different. An exchange always has a created timestamp which is the"
                            + " local timestamp when Camel created the exchange. The message timestamp is only available in some Camel components"
                            + " when the consumer is able to extract the timestamp from the source event."
                            + " If the message has no timestamp, then 0 is returned.", javaType = "long", label = "function")
    public static final String MESSAGE_TIMESTAMP = "messageTimestamp";
    @Metadata(description = "The exchange id", javaType = "String", label = "function")
    public static final String EXCHANGE_ID = "exchangeId";
    @Metadata(description = "The exchange", javaType = "Exchange", label = "function")
    public static final String EXCHANGE = "exchange";
    @Metadata(description = "The exception object on the exchange (also from caught exceptions), is null if no exception present.", javaType = "Exception", label = "function")
    public static final String EXCEPTION = "exception";
    @Metadata(description = "The exception message (also from caught exceptions), is null if no exception present.", javaType = "String", label = "function")
    public static final String EXCEPTION_MESSAGE = "exception.message";
    @Metadata(description = "The exception stacktrace (also from caught exceptions), is null if no exception present.", javaType = "String", label = "function")
    public static final String EXCEPTION_STACKTRACE = "exception.stackTrace";
    @Metadata(description = "Returns the id of the current thread. Can be used for logging.", javaType = "long", label = "function")
    public static final String THREAD_ID = "threadId";
    @Metadata(description = "Returns the name of the current thread. Can be used for logging.", javaType = "String", label = "function")
    public static final String THREAD_NAME = "threadName";
    @Metadata(description = "Returns the local hostname (may be empty if not possible to resolve).", javaType = "String", label = "function")
    public static final String HOST_NAME = "hostName";
    @Metadata(description = "The name of the CamelContext", javaType = "String", label = "function")
    public static final String CAMEL_ID = "camelId";
    @Metadata(description = "The route id of the current route the Exchange is being routed", javaType = "String", label = "function")
    public static final String ROUTE_ID = "routeId";
    @Metadata(description = "The original route id where this exchange was created.", javaType = "String", label = "function")
    public static final String FROM_ROUTE_ID = "fromRouteId";
    @Metadata(description = "Returns the route group of the current route the Exchange is being routed. Not all routes have a group assigned, so this may be null.", javaType = "String", label = "function")
    public static final String ROUTE_GROUP = "routeGroup";
    @Metadata(description = "Returns the id of the current step the Exchange is being routed.", javaType = "String", label = "function")
    public static final String STEP_ID = "stepId";
    @Metadata(description = "Represents a null value", label = "function")
    public static final String NULL = "null";

}
