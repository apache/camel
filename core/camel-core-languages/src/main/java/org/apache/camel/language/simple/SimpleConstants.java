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

    @Metadata(description = "Converts the message body (or expression) to a long number and return the absolute value.",
              label = "number", javaType = "Long", displayName = "Absolute Number")
    public static final String ABS = "abs(exp)";

    @Metadata(description = "Evaluates the expression and throws an exception with the message if the condition is false",
              javaType = "java.lang.Exception", label = "condition")
    public static final String ASSERT = "assert(exp,msg)";

    @Metadata(description = "The DataHandler for the given attachment.", javaType = "jakarta.activation.DataHandler",
              label = "attachment,ognl")
    public static final String ATTACHMENT = "attachment.name";

    @Metadata(description = "All the attachments as a Map<String,DataHandler.", javaType = "java.util.Map",
              label = "attachment")
    public static final String ATTACHMENTS = "attachments";

    @Metadata(description = "The content of the attachment", javaType = "Object", label = "attachment")
    public static final String ATTACHMENTS_CONTENT = "attachmentsContent.name";

    @Metadata(description = "The content of the attachment, converted to the given type.", javaType = "Object",
              label = "attachment")
    public static final String ATTACHMENTS_CONTENT_AS = "attachmentsContentAs(name,type)";

    @Metadata(description = "The content of the attachment as text (ie String).", javaType = "String", label = "attachment")
    public static final String ATTACHMENTS_CONTENT_AS_TEXT = "attachmentsContentAsText.name";

    @Metadata(description = "The attachment header with the given name.", javaType = "String", label = "attachment")
    public static final String ATTACHMENTS_HEADER = "attachmentsHeader(key,name)";

    @Metadata(description = "The attachment header with the given name, converted to the given type.", javaType = "Object",
              label = "attachment")
    public static final String ATTACHMENTS_HEADER_AS = "attachmentsHeaderAs(key,name,type)";

    @Metadata(description = "All the attachment keys (filenames)", javaType = "java.util.Set",
              label = "attachment")
    public static final String ATTACHMENTS_KEYS = "attachmentsKeys";

    @Metadata(description = "The number of attachments. Is 0 if there are no attachments.", javaType = "int",
              label = "attachment")
    public static final String ATTACHMENTS_SIZE = "attachmentsSize";

    @Metadata(description = "Returns the average number from all the values (integral numbers only).", label = "number",
              javaType = "long",
              displayName = "Average Number")
    public static final String AVERAGE = "average(val...)";

    @Metadata(description = "Base64 decodes the message body (or expression)", javaType = "byte[]", label = "base64")
    public static final String BASE64_DECODE = "base64Decode(exp)";

    @Metadata(description = "Base64 encodes the message body (or expression)", javaType = "String", label = "base64")
    public static final String BASE64_ENCODE = "base64Encode(exp)";

    @Metadata(description = "Calls a Java bean. The name of the bean can also refer to a class name using type prefix as follows `bean:type:com.foo.MyClass`. If no method name is given then Camel will automatic attempt to find the best method to use.",
              label = "core", javaType = "Object", displayName = "Call Java Bean")
    public static final String BEAN = "bean(name.method)";

    @Metadata(description = "The message body", javaType = "Object", label = "core,ognl")
    public static final String BODY = "body";

    @Metadata(description = "Converts the message body to the given type (classname).", label = "core,ognl",
              javaType = "Object")
    public static final String BODY_AS = "bodyAs(type)";

    @Metadata(description = "Converts the body to a String and removes all line-breaks, so the string is in one line.",
              javaType = "String", label = "funcorection")
    public static final String BODY_ONE_LINE = "bodyOneLine";

    @Metadata(description = "The message body class type", javaType = "Class", label = "core")
    public static final String BODY_TYPE = "bodyType";

    @Metadata(description = "The Camel Context", label = "core,ognl", javaType = "Object")
    public static final String CAMEL_CONTEXT = "camelContext";

    @Metadata(description = "The name of the CamelContext", javaType = "String", label = "core")
    public static final String CAMEL_ID = "camelId";

    @Metadata(description = "Capitalizes the message body/expression as a String value (upper case every words)",
              javaType = "String", label = "string", displayName = "Capitalize String Values")
    public static final String CAPITALIZE = "capitalize(exp)";

    @Metadata(description = "Converts the message body (or expression) to a floating number and return the ceil value (rounded up to nearest integer).",
              label = "number", javaType = "Integer", displayName = "Ceil Number")
    public static final String CEIL = "ceil(exp)";

    @Metadata(description = "Removes all existing attachments on the message.",
              label = "attachment", javaType = "Object")
    public static final String CLEAR_ATTACHMENTS = "clearAttachments";

    @Metadata(description = "The collate function iterates the message body and groups the data into sub lists of specified size. This can be used with the Splitter EIP to split a message body and group/batch the split sub message into a group of N sub lists.",
              label = "collection", javaType = "java.util.Iterator", displayName = "Group Message Body into Sub Lists")
    public static final String COLLATE = "collate(num)";

    @Metadata(description = "Performs a string concat using two expressions (message body as default) with optional separator",
              label = "string", javaType = "String", displayName = "Concat")
    public static final String CONCAT = "concat(exp,exp,separator)";

    @Metadata(description = "Converts the message body (or expression) to the specified type.", label = "core,ognl",
              displayName = "Convert To")
    public static final String CONVERT_TO = "convertTo(exp,type)";

    @Metadata(description = "Returns true if the message body (or expression) contains part of the text (ignore case)",
              label = "condition", javaType = "boolean")
    public static final String CONTAINS = "contains(exp,text)";

    @Metadata(description = "Evaluates to a java.util.Date object. Supported commands are: `now` for current timestamp, `millis` for current timestamp in millis (unix epoch), `exchangeCreated` for the timestamp when the current exchange was created, `header.xxx` to use the Long/Date object in the header with the key xxx. `variable.xxx` to use the Long/Date in the variable with the key xxx. `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx. `file` for the last modified timestamp of the file (available with a File consumer). Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "date", javaType = "java.util.Date", displayName = "Parse Date")
    public static final String DATE = "date(command)";

    @Metadata(description = "Formats the date to a String using the given date pattern, and with support for timezone. Supported commands are: `now` for current timestamp, `exchangeCreated` for the timestamp when the current exchange was created, `header.xxx` to use the Long/Date object in the header with the key xxx. `variable.xxx` to use the Long/Date in the variable with the key xxx. `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx. `file` for the last modified timestamp of the file (available with a File consumer). Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "date", javaType = "String", displayName = "Date Formatter")
    public static final String DATE_WITH_TIMEZONE = "date-with-timezone(command:timezone:pattern)";

    @Metadata(description = "Returns a set of all the values with duplicates removed",
              label = "collection", javaType = "Set", displayName = "Distinct Values")
    public static final String DISTINCT = "distinct(val...)";

    @Deprecated
    @Metadata(description = "Creates a new empty object (decided by type). Use `string` to create an empty String. Use `list` to create an empty `java.util.ArrayList`. Use `map` to create an empty `java.util.LinkedHashMap`. Use `set` to create an empty `java.util.LinkedHashSet`.",
              label = "collection", javaType = "Object", displayName = "Create Empty Object")
    public static final String EMPTY = "empty(type)";

    @Metadata(description = "The OS environment variable with the given name", label = "other", javaType = "Object",
              displayName = "OS Environment Variable")
    public static final String ENV = "env.name";

    @Metadata(description = "The current exchange", javaType = "org.apache.camel.Exchange", label = "core,ognl")
    public static final String EXCHANGE = "exchange";

    @Metadata(description = "The exchange id", javaType = "String", label = "core")
    public static final String EXCHANGE_ID = "exchangeId";

    @Metadata(description = "The exchange property with the given name", label = "core,ognl", javaType = "Object")
    public static final String EXCHANGE_PROPERTY = "exchangeProperty.name";

    @Metadata(description = "The exception object on the exchange (also from caught exceptions), is null if no exception present.",
              javaType = "java.lang.Exception", label = "core,ognl")
    public static final String EXCEPTION = "exception";

    @Metadata(description = "The exception message (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "core", displayName = "Exception Message")
    public static final String EXCEPTION_MESSAGE = "exception.message";

    @Metadata(description = "The exception stacktrace (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "core", displayName = "Exception Stacktrace")
    public static final String EXCEPTION_STACKTRACE = "exception.stackTrace";

    @Metadata(description = "Returns a List containing the values that satisfy the predicate function (returning true)",
              label = "collection", javaType = "List", displayName = "Filter Elements")
    public static final String FILTER = "filter(exp,fun)";

    @Metadata(description = "Returns a List containing the values returned by the function when applied to each value from the input expression",
              label = "collection", javaType = "List", displayName = "For Each call Function")
    public static final String FOR_EACH = "forEach(exp,fun)";

    @Metadata(description = "Converts the message body (or expression) to a floating number and return the floor value (rounded down to nearest integer).",
              label = "number", javaType = "Integer", displayName = "Floor Number")
    public static final String FLOOR = "floor(exp)";

    @Metadata(description = "The original route id where this exchange was created.", javaType = "String", label = "core")
    public static final String FROM_ROUTE_ID = "fromRouteId";

    @Metadata(description = "Invokes a custom function with the given name using the message body (or expression) as input parameter.",
              label = "core", javaType = "Object", displayName = "For Each call Function")
    public static final String FUNCTION = "function(name,exp)";

    @Metadata(description = "Returns a hashed value (string in hex decimal) of the message body/expression using JDK MessageDigest. The algorithm can be SHA-256 (default) or SHA3-256.",
              label = "other", javaType = "String", displayName = "Compute Hash Value")
    public static final String HASH = "hash(exp,algorithm)";

    @Metadata(description = "The message header with the given name", label = "core,ognl", javaType = "Object")
    public static final String HEADER = "header.name";

    @Metadata(description = "Converts the message header to the given type (classname).", label = "core",
              javaType = "Object")
    public static final String HEADER_AS = "headerAs(key,type)";

    @Metadata(description = "Returns all the message headers in a Map", label = "core", javaType = "java.util.Map")
    public static final String HEADERS = "headers";

    @Metadata(description = "Returns the local hostname (may be empty if not possible to resolve).", javaType = "String",
              label = "other")
    public static final String HOST_NAME = "hostName";

    @Metadata(description = "The message id", javaType = "String", label = "core")
    public static final String ID = "id";

    @Metadata(description = "Evaluates the predicate and returns the value of trueExp or falseExp. This function is similar to the ternary operator in Java.",
              label = "condition", javaType = "Object", displayName = "If Then Else")
    public static final String IIF = "iif(predicate,trueExp,falseExp)";

    @Metadata(description = "Whether the message body (or expression) is alphabetic value (A..Z). For more advanced checks use the `regex` operator.",
              label = "condition", javaType = "boolean", displayName = "Is Alphabetic Value")
    public static final String IS_ALPHA = "isAlpha(exp)";

    @Metadata(description = "Whether the message body (or expression) is alphanumeric value (A..Z0-9). For more advanced checks use the `regex` operator.",
              label = "condition", javaType = "boolean", displayName = "Is Alphabetic-Numeric Value")
    public static final String IS_ALPHA_NUMERIC = "isAlphaNumeric(exp)";

    @Metadata(description = "Whether the message body (or expression) is null or empty (list/map types are tested if they have 0 elements).",
              label = "condition", javaType = "boolean", displayName = "Is Empty")
    public static final String IS_EMPTY = "isEmpty(exp)";

    @Metadata(description = "Whether the message body (or expression) is numeric value (0..9). For more advanced checks use the `regex` operator.",
              label = "condition", javaType = "boolean", displayName = "Is Numeric Value")
    public static final String IS_NUMERIC = "isNumeric(exp)";

    @Metadata(description = "When working with JSon data, then this allows using the JQ language, for example, to extract data from the message body (in JSon format). This requires having camel-jq JAR on the classpath. For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "json", javaType = "Object", displayName = "JQ")
    public static final String JQ = "jq(input,exp)";

    @Metadata(description = "When working with JSon data, then this allows using the JSonPath language, for example, to extract data from the message body (in JSon format). This requires having camel-jsonpath JAR on the classpath. For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "json", javaType = "Object", displayName = "JSonPath")
    public static final String JSONPATH = "jsonpath(input,exp)";

    @Metadata(description = "The join function iterates the message body/expression and joins the data into a string. The separator is by default a comma. The prefix is optional. The join uses the message body as source by default. It is possible to refer to another source (simple language) such as a header via the exp parameter. For example `join('&','id=','$\\{header.ids}')`.",
              label = "string", javaType = "String")
    public static final String JOIN = "join(separator,prefix,exp)";

    @Metadata(description = "The payload length (number of bytes) of the message body (or expression).", label = "function",
              javaType = "core", displayName = "Length")
    public static final String LENGTH = "length(exp)";

    @Metadata(description = "The list function creates an ArrayList with the given set of values.", label = "collection",
              javaType = "java.util.ArrayList", displayName = "Create List of values")
    public static final String LIST = "list(val...)";

    @Metadata(description = "Loads the content of the resource from classpath (cannot load from file-system to avoid dangerous situations).",
              label = "core", javaType = "String", displayName = "Load")
    public static final String LOAD = "load(file)";

    @Metadata(description = "Lowercases the message body (or expression)", label = "string", javaType = "String",
              displayName = "Lowercase")
    public static final String LOWERCASE = "lowercase(exp)";

    @Metadata(description = "Converts the message body to the given type (classname). If the body is null then the function will fail with an exception",
              label = "core,ognl", javaType = "Object")
    public static final String MANDATORY_BODY_AS = "mandatoryBodyAs(type)";

    @Metadata(description = "The map function creates a LinkedHashMap with the given set of pairs.", label = "collection",
              javaType = "java.util.LinkedHashMap", displayName = "Create Map of pairs")
    public static final String MAP = "map(key1,value1,...)";

    @Metadata(description = "Returns the maximum number from all the values (integral numbers only).", label = "number",
              javaType = "long",
              displayName = "Maximum Number")
    public static final String MAX = "max(val...)";

    @Metadata(description = "Converts the message to the given type (classname).", label = "core,ognl", javaType = "Object")
    public static final String MESSAGE_AS = "messageAs(type)";

    @Metadata(description = "The message history of the current exchange (how it has been routed). This is similar to the route stack-trace message history the error handler logs in case of an unhandled exception. The boolean can be used to turn off detailed information to be less verbose, and avoid printing sensitive data from the message.",
              label = "core", javaType = "String", displayName = "Print Message History")
    public static final String MESSAGE_HISTORY = "messageHistory(boolean)";

    @Metadata(description = "The message timestamp (millis since epoc) that this message originates from. Some systems like JMS, Kafka, AWS have a timestamp on the event/message that Camel received. This method returns the timestamp if a timestamp exists. The message timestamp and exchange created are different. An exchange always has a created timestamp which is the local timestamp when Camel created the exchange. The message timestamp is only available in some Camel components when the consumer is able to extract the timestamp from the source event. If the message has no timestamp, then 0 is returned.",
              javaType = "long", label = "core")
    public static final String MESSAGE_TIMESTAMP = "messageTimestamp";

    @Metadata(description = "Returns the minimum number from all the values (integral numbers only).", label = "number",
              javaType = "long",
              displayName = "Minimum Number")
    public static final String MIN = "min(val...)";

    @Metadata(description = "Creates a new empty object (decided by type). Use `string` to create an empty String. Use `list` to create an empty `java.util.ArrayList`. Use `map` to create an empty `java.util.LinkedHashMap`. Use `set` to create an empty `java.util.LinkedHashSet`.",
              label = "collection", javaType = "Object", displayName = "Create Empty Object")
    public static final String NEW_EMPTY = "newEmpty(type)";

    @Metadata(description = "Normalizes the whitespace in the message body (or expression) by cleaning up excess whitespaces.",
              label = "string", javaType = "String", displayName = "Normalize Whitespace")
    public static final String NORMALIZE_WHITESPACE = "normalizeWhitespace(exp)";

    @Metadata(description = "Evaluates the predicate and returns the opposite.", label = "condition", javaType = "boolean")
    public static final String NOT = "not";

    @Metadata(description = "Returns a null value", label = "other", javaType = "Object")
    public static final String NULL = "null";

    @Metadata(description = "The original incoming body (only available if allowUseOriginalMessage=true).", javaType = "Object",
              label = "core")
    public static final String ORIGINAL_BODY = "originalBody";

    @Metadata(description = "Pads the expression with extra padding if necessary, according the the total width. The separator is by default a space. If the width is negative then padding to the right, otherwise to the left.",
              label = "string", javaType = "String", displayName = "Pad String")
    public static final String PAD = "pad(exp,width,separator)";

    @Metadata(description = "Converts the expression to a String, and attempts to pretty print if JSon or XML, otherwise the expression is returned as the String value.",
              label = "json,xml", javaType = "String", displayName = "Pretty Print")
    public static final String PRETTY = "pretty(exp)";

    @Metadata(description = "Converts the body to a String, and attempts to pretty print if JSon or XML; otherwise the body is returned as the String value.",
              javaType = "String", label = "json,xml")
    public static final String PRETTY_BODY = "prettyBody";

    @Metadata(description = "Lookup a property placeholder with the given key. If the key does not exist nor has a value, then an optional default value can be specified.",
              label = "core", javaType = "String", displayName = "Property Placeholder")
    public static final String PROPERTIES = "properties:key:default";

    @Metadata(description = "Checks whether a property placeholder with the given key exists or not. The result can be negated by prefixing the key with !",
              label = "core", javaType = "boolean", displayName = "Property Placeholder Exists")
    public static final String PROPERTIES_EXIST = "propertiesExist:key";

    @Metadata(description = "Returns the message body (or expression) as a double quoted string",
              label = "string", javaType = "String")
    public static final String QUOTE = "quote(exp)";

    @Metadata(description = "Returns a random number between min and max (exclusive)",
              label = "number", javaType = "int")
    public static final String RANDOM = "random(min,max)";

    @Metadata(description = "Returns a list of increasing integers between the given interval (exclusive)",
              label = "number", javaType = "List")
    public static final String RANGE = "range(min,max)";

    @Metadata(description = "To look up a bean from the Registry with the given name.", label = "core", javaType = "Object",
              displayName = "Bean By Id")
    public static final String REF = "ref:name";

    @Metadata(description = "Replace all the string values in the message body/expression. To make it easier to replace single and double quotes, then you can use XML escaped values `\\&quot;` as double quote, `\\&apos;` as single quote, and `\\&empty;` as empty value.",
              label = "string", javaType = "String", displayName = "Replace String Values")
    public static final String REPLACE = "replace(from,to,exp)";

    @Metadata(description = "Returns a list of all the values, but in reverse order",
              label = "collection", javaType = "List", displayName = "Reverse Values")
    public static final String REVERSE = "reverse(val...)";

    @Metadata(description = "The route group of the current route the Exchange is being routed. Not all routes have a group assigned, so this may be null.",
              javaType = "String", label = "core")
    public static final String ROUTE_GROUP = "routeGroup";

    @Metadata(description = "The route id of the current route the Exchange is being routed", javaType = "String",
              label = "core")
    public static final String ROUTE_ID = "routeId";

    @Metadata(description = "Returns the message body (or expression) safely quoted if needed",
              label = "string", javaType = "String")
    public static final String SAFE_QUOTE = "safeQuote(exp)";

    @Metadata(description = "Sets an attachment with payload from the message body/expression.",
              label = "attachment", javaType = "Object")
    public static final String SET_ATTACHMENT = "setVariable(key,exp)";

    @Metadata(description = "Sets a message header with the given expression (optional converting to the given type)",
              label = "core", javaType = "Object")
    public static final String SET_HEADER = "setHeader(name,type,exp)";

    @Metadata(description = "Sets a variable with the given expression (optional converting to the given type)",
              label = "core", javaType = "Object")
    public static final String SET_VARIABLE = "setVariable(name,type,exp)";

    @Metadata(description = "Returns a list of all the values shuffled in random order",
              label = "collection", javaType = "List", displayName = "Shuffle Values")
    public static final String SHUFFLE = "shuffle(val...)";

    @Metadata(description = "Returns the number of elements in collection or array based payloads. If the value is null then 0 is returned, otherwise 1.",
              label = "collection", javaType = "int", displayName = "Size")
    public static final String SIZE = "size(exp)";

    @Metadata(description = "The skip function iterates the message body and skips the first number of items. This can be used with the Splitter EIP to split a message body and skip the first N number of items.",
              label = "collection", javaType = "java.util.Iterator", displayName = "Skip First Items from the Message Body")
    public static final String SKIP = "skip(num)";

    @Metadata(description = "Splits the message body/expression as a String value using the separator into a String array",
              label = "collection", javaType = "String[]", displayName = "Split String Values")
    public static final String SPLIT = "split(exp,separator)";

    @Metadata(description = "Returns the id of the current step the Exchange is being routed.", javaType = "String",
              label = "core")
    public static final String STEP_ID = "stepId";

    @Metadata(description = "Returns a substring of the message body/expression. If only one positive number, then the returned string is clipped from the beginning. If only one negative number, then the returned string is clipped from the beginning. Otherwise the returned string is clipped between the head and tail positions.",
              label = "string", javaType = "String")
    public static final String SUBSTRING = "substring(head,tail)";

    @Metadata(description = "Returns a substring of the message body/expression that comes after. Returns null if nothing comes after.",
              label = "string", javaType = "String", displayName = "Substring After")
    public static final String SUBSTRING_AFTER = "substringAfter(exp,before)";

    @Metadata(description = "Returns a substring of the message body/expression that comes before. Returns null if nothing comes before.",
              label = "string", javaType = "String", displayName = "Substring Before")
    public static final String SUBSTRING_BEFORE = "substringBefore(exp,before)";

    @Metadata(description = "Returns a substring of the message body/expression that are between after and before. Returns null if nothing comes between.",
              label = "string", javaType = "String")
    public static final String SUBSTRING_BETWEEN = "substringBetween(exp,after,before)";

    @Metadata(description = "Sums together all the values as integral numbers. This function can also be used to subtract by using negative numbers.",
              label = "number", javaType = "long", displayName = "Calculate Sum Number")
    public static final String SUM = "sum(val...)";

    @Metadata(description = "The JVM system property with the given name", label = "other", javaType = "Object",
              displayName = "JVM System Property")
    public static final String SYS = "sys.name";

    @Metadata(description = "Returns the id of the current thread. Can be used for logging.", javaType = "long",
              label = "other")
    public static final String THREAD_ID = "threadId";

    @Metadata(description = "Returns the name of the current thread. Can be used for logging.", javaType = "String",
              label = "other")
    public static final String THREAD_NAME = "threadName";

    @Metadata(description = "Deliberately throws an error. Uses IllegalArgumentException by default if no type is specified (use fully qualified classname).",
              javaType = "java.lang.Exception", label = "core")
    public static final String THROW_EXCEPTION = "throwException(type,msg)";

    @Metadata(description = "The trim function trims the message body (or expression) by removing all leading and trailing white spaces.",
              label = "string", javaType = "String", displayName = "Trim")
    public static final String TRIM = "trim(exp)";

    @Metadata(description = "To refer to a type or field by its classname. To refer to a field, you can append .FIELD_NAME. For example, you can refer to the constant field from Exchange as: `org.apache.camel.Exchange.FILE_NAME`",
              label = "core", javaType = "Object", displayName = "Java Field Value")
    public static final String TYPE = "type:name.field";

    @Metadata(description = "What kind of type is the value (null,number,string,boolean,array,object)",
              label = "core", javaType = "Object", displayName = "Kind of Type")
    public static final String KIND_OF_TYPE = "kindOfType(exp)";

    @Metadata(description = "Returns the message body (or expression) with any leading/ending quotes removed",
              label = "string", javaType = "String")
    public static final String UNQUOTE = "unquote(exp)";

    @Metadata(description = "Uppercases the message body (or expression)", label = "string", javaType = "String",
              displayName = "Uppercase")
    public static final String UPPERCASE = "uppercase(exp)";

    @Metadata(description = "Returns a UUID using the Camel `UuidGenerator`. You can choose between `default`, `classic`, `short` and `simple` as the type. If no type is given, the default is used. It is also possible to use a custom `UuidGenerator` and bind the bean to the Registry with an id. For example `${uuid(myGenerator)}` where the ID is _myGenerator_.",
              label = "other", javaType = "String", displayName = "Generate UUID")
    public static final String UUID = "uuid(type)";

    @Metadata(description = "Returns the expression as a constant value",
              label = "core", javaType = "Object", displayName = "Value")
    public static final String VAL = "val(exp)";

    @Metadata(description = "The variable with the given name", label = "core,ognl", javaType = "Object")
    public static final String VARIABLE = "variable.name";

    @Metadata(description = "Converts the variable to the given type (classname).", label = "core", javaType = "Object")
    public static final String VARIABLE_AS = "variableAs(key,type)";

    @Metadata(description = "Returns all the variables from the current Exchange in a Map", label = "core",
              javaType = "java.util.Map")
    public static final String VARIABLES = "variables";

    @Metadata(description = "When working with XML data, then this allows using the XPath language, for example, to extract data from the message body (in XML format). This requires having camel-xpath JAR on the classpath. For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the XML payload instead of the message body.",
              label = "xml", javaType = "Object", displayName = "XPath")
    public static final String XPATH = "xpath(input,exp)";
}
