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

@Metadata(label = "function", annotations = { "prefix=${", "suffix=}" })
public final class SimpleConstants {

    @Metadata(description = "The message body", javaType = "Object", label = "function")
    public static final String BODY = "body";
    @Metadata(description = "Converts the body to a String, and attempts to pretty print if JSon or XML; otherwise the body is returned as the String value.",
              javaType = "String", label = "function")
    public static final String PRETTY_BODY = "prettyBody";
    @Metadata(description = "Converts the body to a String and removes all line-breaks, so the string is in one line.",
              javaType = "String", label = "function")
    public static final String BODY_ONE_LINE = "bodyOneLine";
    @Metadata(description = "The original incoming body (only available if allowUseOriginalMessage=true).", javaType = "Object",
              label = "function")
    public static final String ORIGINAL_BODY = "originalBody";
    @Metadata(description = "The message id", javaType = "String", label = "function")
    public static final String ID = "id";
    @Metadata(description = "The message timestamp (millis since epoc) that this message originates from."
                            + " Some systems like JMS, Kafka, AWS have a timestamp on the event/message that Camel received. This method returns the timestamp if a timestamp exists."
                            + " The message timestamp and exchange created are different. An exchange always has a created timestamp which is the"
                            + " local timestamp when Camel created the exchange. The message timestamp is only available in some Camel components"
                            + " when the consumer is able to extract the timestamp from the source event."
                            + " If the message has no timestamp, then 0 is returned.",
              javaType = "long", label = "function")
    public static final String MESSAGE_TIMESTAMP = "messageTimestamp";
    @Metadata(description = "The exchange id", javaType = "String", label = "function")
    public static final String EXCHANGE_ID = "exchangeId";
    @Metadata(description = "The exchange", javaType = "org.apache.camel.Exchange", label = "function")
    public static final String EXCHANGE = "exchange";
    @Metadata(description = "The exception object on the exchange (also from caught exceptions), is null if no exception present.",
              javaType = "java.lang.Exception", label = "function")
    public static final String EXCEPTION = "exception";
    @Metadata(description = "The exception message (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "function")
    public static final String EXCEPTION_MESSAGE = "exception.message";
    @Metadata(description = "The exception stacktrace (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "function")
    public static final String EXCEPTION_STACKTRACE = "exception.stackTrace";
    @Metadata(description = "Returns the id of the current thread. Can be used for logging.", javaType = "long",
              label = "function")
    public static final String THREAD_ID = "threadId";
    @Metadata(description = "Returns the name of the current thread. Can be used for logging.", javaType = "String",
              label = "function")
    public static final String THREAD_NAME = "threadName";
    @Metadata(description = "Returns the local hostname (may be empty if not possible to resolve).", javaType = "String",
              label = "function")
    public static final String HOST_NAME = "hostName";
    @Metadata(description = "The name of the CamelContext", javaType = "String", label = "function")
    public static final String CAMEL_ID = "camelId";
    @Metadata(description = "The route id of the current route the Exchange is being routed", javaType = "String",
              label = "function")
    public static final String ROUTE_ID = "routeId";
    @Metadata(description = "The original route id where this exchange was created.", javaType = "String", label = "function")
    public static final String FROM_ROUTE_ID = "fromRouteId";
    @Metadata(description = "Returns the route group of the current route the Exchange is being routed. Not all routes have a group assigned, so this may be null.",
              javaType = "String", label = "function")
    public static final String ROUTE_GROUP = "routeGroup";
    @Metadata(description = "Returns the id of the current step the Exchange is being routed.", javaType = "String",
              label = "function")
    public static final String STEP_ID = "stepId";
    @Metadata(description = "Represents a null value", label = "function", javaType = "Object")
    public static final String NULL = "null";
    @Metadata(description = "Converts the message to the given type (classname).", label = "function", javaType = "Object")
    public static final String MESSAGE_AS = "messageAs(type)";
    @Metadata(description = "Converts the message body to the given type (classname).", label = "function", javaType = "Object")
    public static final String BODY_AS = "bodyAs(type)";
    @Metadata(description = "Converts the message body to the given type (classname). If the body is null then the function will fail with an exception",
              label = "function", javaType = "Object")
    public static final String MANDATORY_BODY_AS = "mandatoryBodyAs(type)";
    @Metadata(description = "Converts the message header to the given type (classname).", label = "function",
              javaType = "Object")
    public static final String HEADER_AS = "headerAs(key,type)";
    @Metadata(description = "Returns all the message headers in a Map", label = "function", javaType = "java.util.Map")
    public static final String HEADERS = "headers";
    @Metadata(description = "Converts the variable to the given type (classname).", label = "function",
              javaType = "Object")
    public static final String VARIABLE_AS = "variableAs(key,type)";
    @Metadata(description = "Returns all the variables from the current Exchange in a Map", label = "function",
              javaType = "java.util.Map")
    public static final String VARIABLES = "variables";
    @Metadata(description = "When working with JSon data, then this allows using the JQ language, for example, to extract data from the message body (in JSon format). This requires having camel-jq JAR on the classpath.",
              label = "function", javaType = "Object")
    public static final String JQ = "jq(exp)";
    @Metadata(description = "When working with JSon data, then this allows using the JQ language, for example, to extract data from the message body (in JSon format). This requires having camel-jq JAR on the classpath."
                            + " For input, you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "function", javaType = "Object")
    public static final String JQ_INPUT = "jq(input,exp)";
    @Metadata(description = "When working with JSon data, then this allows using the JSonPath language, for example, to extract data from the message body (in JSon format). This requires having camel-jsonpath JAR on the classpath.",
              label = "function", javaType = "Object")
    public static final String JSONPATH = "jsonpath(exp)";
    @Metadata(description = "When working with JSon data, then this allows using the JSonPath language, for example, to extract data from the message body (in JSon format). This requires having camel-jsonpath JAR on the classpath."
                            + " For input, you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "function", javaType = "Object")
    public static final String JSONPATH_INPUT = "jsonpath(input,exp)";
    @Metadata(description = "When working with XML data, then this allows using the XPath language, for example, to extract data from the message body (in XML format). This requires having camel-xpath JAR on the classpath.",
              label = "function", javaType = "Object")
    public static final String XPATH = "xpath(exp)";
    @Metadata(description = "When working with XML data, then this allows using the XPath language, for example, to extract data from the message body (in XML format). This requires having camel-xpath JAR on the classpath."
                            + " For input, you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the XML payload instead of the message body.",
              label = "function", javaType = "Object")
    public static final String XPATH_INPUT = "xpath(input,exp)";
    @Metadata(description = "The JVM system property with the given name", label = "function", javaType = "Object")
    public static final String SYS = "sys.name";
    @Metadata(description = "The OS environment variable with the given name", label = "function", javaType = "Object")
    public static final String ENV = "env.name";
    @Metadata(description = "Converts the expression to a String, and attempts to pretty print if JSon or XML, otherwise the expression is returned as the String value.",
              label = "function", javaType = "String")
    public static final String PRETTY = "pretty(exp)";
    @Metadata(description = "Evaluates to a java.util.Date object."
                            + " Supported commands are: `now` for current timestamp,"
                            + " `exchangeCreated` for the timestamp when the current exchange was created,"
                            + " `header.xxx` to use the Long/Date object in the header with the key xxx."
                            + " `variable.xxx` to use the Long/Date in the variable with the key xxx."
                            + " `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx."
                            + " `file` for the last modified timestamp of the file (available with a File consumer)."
                            + " Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "function", javaType = "java.util.Date")
    public static final String DATE = "date(command)";
    @Metadata(description = "Formats the date to a String using the given date pattern, and with support for timezone."
                            + " Supported commands are: `now` for current timestamp,"
                            + " `exchangeCreated` for the timestamp when the current exchange was created,"
                            + " `header.xxx` to use the Long/Date object in the header with the key xxx."
                            + " `variable.xxx` to use the Long/Date in the variable with the key xxx."
                            + " `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx."
                            + " `file` for the last modified timestamp of the file (available with a File consumer)."
                            + " Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "function", javaType = "String")
    public static final String DATE_WITH_TIMEZONE = "date-with-timezone(command:timezone:pattern)";
    @Metadata(description = "Calls a Java bean."
                            + " The name of the bean can also refer to a class name using type prefix as follows `bean:type:com.foo.MyClass`."
                            + " If no method name is given then Camel will automatic attempt to find the best method to use.",
              label = "function", javaType = "Object")
    public static final String BEAN = "bean(name.method)";
    @Metadata(description = "Checks whether a property placeholder with the given key exists or not. The result can be negated by prefixing the key with !",
              label = "function", javaType = "boolean")
    public static final String PROPERTIES_EXIST = "propertiesExist:key";
    @Metadata(description = "Lookup a property placeholder with the given key. If the key does not exist nor has a value, then an optional default value can be specified.",
              label = "function", javaType = "String")
    public static final String PROPERTIES = "properties:key:default";
    @Metadata(description = "To look up a bean from the Registry with the given name.", label = "function", javaType = "Object")
    public static final String REF = "ref:name";
    @Metadata(description = "To refer to a type or field by its classname. To refer to a field, you can append .FIELD_NAME. For example, you can refer to the"
                            + " constant field from Exchange as: `org.apache.camel.Exchange.FILE_NAME`",
              label = "function", javaType = "Object")
    public static final String TYPE = "type:name.field";

}
