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

    @Metadata(description = "The message body", javaType = "Object", label = "function,ognl")
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
    @Metadata(description = "The current exchange", javaType = "org.apache.camel.Exchange", label = "function,ognl")
    public static final String EXCHANGE = "exchange";
    @Metadata(description = "The exception object on the exchange (also from caught exceptions), is null if no exception present.",
              javaType = "java.lang.Exception", label = "function,ognl")
    public static final String EXCEPTION = "exception";
    @Metadata(description = "The exception message (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "function", displayName = "Exception Message")
    public static final String EXCEPTION_MESSAGE = "exception.message";
    @Metadata(description = "The exception stacktrace (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "function", displayName = "Exception Stacktrace")
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
    @Metadata(description = "Converts the message to the given type (classname).", label = "function,ognl", javaType = "Object")
    public static final String MESSAGE_AS = "messageAs(type)";
    @Metadata(description = "Converts the message body to the given type (classname).", label = "function,ognl",
              javaType = "Object")
    public static final String BODY_AS = "bodyAs(type)";
    @Metadata(description = "Converts the message body to the given type (classname). If the body is null then the function will fail with an exception",
              label = "function,ognl", javaType = "Object")
    public static final String MANDATORY_BODY_AS = "mandatoryBodyAs(type)";
    @Metadata(description = "Converts the message header to the given type (classname).", label = "function",
              javaType = "Object")
    public static final String HEADER_AS = "headerAs(key,type)";
    @Metadata(description = "Returns all the message headers in a Map", label = "function", javaType = "java.util.Map")
    public static final String HEADERS = "headers";
    @Metadata(description = "The message header with the given name", label = "function,ognl", javaType = "Object")
    public static final String HEADER = "header.name";
    @Metadata(description = "Converts the variable to the given type (classname).", label = "function", javaType = "Object")
    public static final String VARIABLE_AS = "variableAs(key,type)";
    @Metadata(description = "Returns all the variables from the current Exchange in a Map", label = "function",
              javaType = "java.util.Map")
    public static final String VARIABLES = "variables";
    @Metadata(description = "The variable with the given name", label = "function,ognl", javaType = "Object")
    public static final String VARIABLE = "variable.name";
    @Metadata(description = "The exchange property with the given name", label = "function,ognl", javaType = "Object")
    public static final String EXCHANGE_PROPERTY = "exchangeProperty.name";
    @Metadata(description = "The Camel Context", label = "function,ognl", javaType = "Object")
    public static final String CAMEL_CONTEXT = "camelContext";
    @Metadata(description = "When working with JSon data, then this allows using the JQ language, for example, to extract data from the message body (in JSon format). This requires having camel-jq JAR on the classpath."
                            + " For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "function", javaType = "Object", displayName = "JQ")
    public static final String JQ = "jq(input,exp)";
    @Metadata(description = "When working with JSon data, then this allows using the JSonPath language, for example, to extract data from the message body (in JSon format). This requires having camel-jsonpath JAR on the classpath."
                            + " For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "function", javaType = "Object", displayName = "JSonPath")
    public static final String JSONPATH = "jsonpath(input,exp)";
    @Metadata(description = "When working with XML data, then this allows using the XPath language, for example, to extract data from the message body (in XML format). This requires having camel-xpath JAR on the classpath."
                            + " For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the XML payload instead of the message body.",
              label = "function", javaType = "Object", displayName = "XPath")
    public static final String XPATH = "xpath(input,exp)";
    @Metadata(description = "The JVM system property with the given name", label = "function", javaType = "Object",
              displayName = "JVM System Property")
    public static final String SYS = "sys.name";
    @Metadata(description = "The OS environment variable with the given name", label = "function", javaType = "Object",
              displayName = "OS Environment Variable")
    public static final String ENV = "env.name";
    @Metadata(description = "Converts the expression to a String, and attempts to pretty print if JSon or XML, otherwise the expression is returned as the String value.",
              label = "function", javaType = "String", displayName = "Pretty Print")
    public static final String PRETTY = "pretty(exp)";
    @Metadata(description = "Evaluates to a java.util.Date object." + " Supported commands are: `now` for current timestamp,"
                            + " `exchangeCreated` for the timestamp when the current exchange was created,"
                            + " `header.xxx` to use the Long/Date object in the header with the key xxx."
                            + " `variable.xxx` to use the Long/Date in the variable with the key xxx."
                            + " `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx."
                            + " `file` for the last modified timestamp of the file (available with a File consumer)."
                            + " Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "function", javaType = "java.util.Date", displayName = "Parse Date")
    public static final String DATE = "date(command)";
    @Metadata(description = "Formats the date to a String using the given date pattern, and with support for timezone."
                            + " Supported commands are: `now` for current timestamp,"
                            + " `exchangeCreated` for the timestamp when the current exchange was created,"
                            + " `header.xxx` to use the Long/Date object in the header with the key xxx."
                            + " `variable.xxx` to use the Long/Date in the variable with the key xxx."
                            + " `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx."
                            + " `file` for the last modified timestamp of the file (available with a File consumer)."
                            + " Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "function", javaType = "String", displayName = "Date Formatter")
    public static final String DATE_WITH_TIMEZONE = "date-with-timezone(command:timezone:pattern)";
    @Metadata(description = "Calls a Java bean."
                            + " The name of the bean can also refer to a class name using type prefix as follows `bean:type:com.foo.MyClass`."
                            + " If no method name is given then Camel will automatic attempt to find the best method to use.",
              label = "function", javaType = "Object", displayName = "Call Java Bean")
    public static final String BEAN = "bean(name.method)";
    @Metadata(description = "Checks whether a property placeholder with the given key exists or not. The result can be negated by prefixing the key with !",
              label = "function", javaType = "boolean", displayName = "Property Placeholder Exists")
    public static final String PROPERTIES_EXIST = "propertiesExist:key";
    @Metadata(description = "Lookup a property placeholder with the given key. If the key does not exist nor has a value, then an optional default value can be specified.",
              label = "function", javaType = "String", displayName = "Property Placeholder")
    public static final String PROPERTIES = "properties:key:default";
    @Metadata(description = "To look up a bean from the Registry with the given name.", label = "function", javaType = "Object",
              displayName = "Bean By Id")
    public static final String REF = "ref:name";
    @Metadata(description = "To refer to a type or field by its classname. To refer to a field, you can append .FIELD_NAME. For example, you can refer to the"
                            + " constant field from Exchange as: `org.apache.camel.Exchange.FILE_NAME`",
              label = "function", javaType = "Object", displayName = "Java Field Value")
    public static final String TYPE = "type:name.field";
    @Metadata(description = "Replace all the string values in the message body/expression."
                            + " To make it easier to replace single and double quotes, then you can use XML escaped values `\\&quot;` as double quote, `\\&apos;` as single quote, and `\\&empty;` as empty value.",
              label = "function", javaType = "String", displayName = "Replace String Values")
    public static final String REPLACE = "replace(from,to,exp)";
    @Metadata(description = "Returns a substring of the message body/expression."
                            + " If only one positive number, then the returned string is clipped from the beginning."
                            + " If only one negative number, then the returned string is clipped from the beginning."
                            + " Otherwise the returned string is clipped between the head and tail positions.",
              label = "function", javaType = "String")
    public static final String SUBSTRING = "substring(head,tail)";
    @Metadata(description = "Returns a random number between min (included) and max (excluded).", label = "function",
              javaType = "int", displayName = "Generate Random Number")
    public static final String RANDOM = "random(min,max)";
    @Metadata(description = "The skip function iterates the message body and skips the first number of items."
                            + " This can be used with the Splitter EIP to split a message body and skip the first N number of items.",
              label = "function", javaType = "java.util.Iterator", displayName = "Skip First Items from the Message Body")
    public static final String SKIP = "skip(num)";
    @Metadata(description = "The collate function iterates the message body and groups the data into sub lists of specified size."
                            + " This can be used with the Splitter EIP to split a message body and group/batch"
                            + " the split sub message into a group of N sub lists.",
              label = "function", javaType = "java.util.Iterator", displayName = "Group Message Body into Sub Lists")
    public static final String COLLATE = "collate(num)";
    @Metadata(description = "The join function iterates the message body/expression and joins the data into a string."
                            + " The separator is by default a comma. The prefix is optional."
                            + " The join uses the message body as source by default. It is possible to refer to another"
                            + " source (simple language) such as a header via the exp parameter. For example `join('&','id=','$\\{header.ids}')`.",
              label = "function", javaType = "String")
    public static final String JOIN = "join(separator,prefix,exp)";
    @Metadata(description = "The message history of the current exchange (how it has been routed). This is similar to the route stack-trace message history"
                            + " the error handler logs in case of an unhandled exception."
                            + " The boolean can be used to turn off detailed information to be less verbose, and avoid printing sensitive data from the message.",
              label = "function", javaType = "String", displayName = "Print Message History")
    public static final String MESSAGE_HISTORY = "messageHistory(boolean)";
    @Metadata(description = "Returns a UUID using the Camel `UuidGenerator`."
                            + " You can choose between `default`, `classic`, `short` and `simple` as the type."
                            + " If no type is given, the default is used. It is also possible to use a custom `UuidGenerator`"
                            + " and bind the bean to the xref:manual::registry.adoc[Registry] with an id. For example `${uuid(myGenerator)}`"
                            + " where the ID is _myGenerator_.",
              label = "function", javaType = "String", displayName = "Generate UUID")
    public static final String UUID = "uuid(type)";
    @Metadata(description = "Returns a hashed value (string in hex decimal) of the message body/expression using JDK MessageDigest. The algorithm can be SHA-256 (default) or SHA3-256.",
              label = "function", javaType = "String", displayName = "Compute Hash Value")
    public static final String HASH = "hash(exp,algorithm)";
    @Metadata(description = "Creates a new empty object (decided by type). Use `string` to create an empty String. Use `list` to create an empty `java.util.ArrayList`. Use `map` to create an empty `java.util.LinkedHashMap`.",
              label = "function", javaType = "Object", displayName = "Create Empty Object")
    public static final String EMPTY = "empty(type)";
    @Metadata(description = "Evaluates the predicate and returns the value of trueExp or falseExp. This function is similar to the ternary operator in Java.",
              label = "function", javaType = "Object", displayName = "If Then Else")
    public static final String IIF = "iif(predicate,trueExp,falseExp)";
    @Metadata(description = "The list function creates an ArrayList with the given set of values.",
              label = "function", javaType = "java.util.ArrayList", displayName = "Create List of values")
    public static final String LIST = "list(val...)";
    @Metadata(description = "The map function creates a LinkedHashMap with the given set of pairs.",
              label = "function", javaType = "java.util.LinkedHashMap", displayName = "Create Map of pairs")
    public static final String MAP = "map(key1,value1,...)";
    @Metadata(description = "All the attachments as a Map<String,DataHandler.", javaType = "java.util.Map", label = "function")
    public static final String ATTACHMENTS = "attachments";
    @Metadata(description = "The number of attachments. Is 0 if there are no attachments.", javaType = "int",
              label = "function")
    public static final String ATTACHMENTS_SIZE = "attachments.size";
    @Metadata(description = "The content of the attachment as text (ie String).", javaType = "String", label = "function")
    public static final String ATTACHMENTS_CONTENT_AS_TEXT = "attachmentContentAsText";
    @Metadata(description = "The content of the attachment", javaType = "Object", label = "function")
    public static final String ATTACHMENTS_CONTENT = "attachmentContent";
    @Metadata(description = "The content of the attachment, converted to the given type.", javaType = "Object",
              label = "function")
    public static final String ATTACHMENTS_CONTENT_AS = "attachmentContentAs(type)";
    @Metadata(description = "The attachment header with the given name.", javaType = "String", label = "function")
    public static final String ATTACHMENTS_HEADER = "attachmentHeader(key,name)";
    @Metadata(description = "The attachment header with the given name, converted to the given type.", javaType = "Object",
              label = "function")
    public static final String ATTACHMENTS_HEADER_AS = "attachmentHeader(key,name,type)";
    @Metadata(description = "The DataHandler for the given attachment.", javaType = "jakarta.activation.DataHandler",
              label = "function,ognl")
    public static final String ATTACHMENT = "attachment(key)";

}
