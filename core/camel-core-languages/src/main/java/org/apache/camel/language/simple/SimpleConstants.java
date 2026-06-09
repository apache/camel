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
              label = "number", javaType = "Long", displayName = "Absolute Number",
              examples = { "${abs(-5)} -> 5", "${abs(${header.price})} -> 42 // when header price is -42" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String ABS = "abs(exp)";

    @Metadata(description = "Evaluates the expression and throws an exception with the message if the condition is false",
              javaType = "java.lang.Exception", label = "condition",
              examples = { "${assert(${body} == 'Hello', 'Must be Hello')}" },
              annotations = {
                      "param=exp:Object:required::The predicate expression to evaluate",
                      "param=msg:String:optional::The error message if the assertion fails" })
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
              javaType = "long", displayName = "Average Number",
              examples = { "${average(10,20,30)} -> 20", "${average()} -> 20 // when body is list [10,20,30]" },
              annotations = {
                      "param=val:long:optional:body:Values to average. When omitted uses the message body (list or array)" })
    public static final String AVERAGE = "average(val...)";

    @Metadata(description = "Base64 decodes the message body (or expression)", javaType = "byte[]", label = "base64",
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String BASE64_DECODE = "base64Decode(exp)";

    @Metadata(description = "Base64 encodes the message body (or expression)", javaType = "String", label = "base64",
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String BASE64_ENCODE = "base64Encode(exp)";

    @Metadata(description = "Calls a Java bean. The name of the bean can also refer to a class name using type prefix as follows `bean:type:com.foo.MyClass`. If no method name is given then Camel will automatic attempt to find the best method to use.",
              label = "core", javaType = "Object", displayName = "Call Java Bean",
              examples = {
                      "${bean(myBean)} -> result // calls best matching method",
                      "${bean(myBean.myMethod)} -> result // calls specific method",
                      "${bean(type:com.foo.MyClass.myMethod)} -> result // calls static method by classname" },
              annotations = {
                      "param=name:String:required::The bean name (or type:classname)",
                      "param=method:String:optional::The method name to invoke" })
    public static final String BEAN = "bean(name.method)";

    @Metadata(description = "The message body", javaType = "Object", label = "core,ognl",
              examples = { "${body} -> Hello World // when body is the string 'Hello World'" })
    public static final String BODY = "body";

    @Metadata(description = "Converts the message body to the given type (classname).", label = "core,ognl",
              javaType = "Object",
              examples = {
                      "${bodyAs(String)} -> 42 // converts integer body to string",
                      "${bodyAs(int)} -> 42 // converts string body to integer" },
              annotations = { "param=type:String:required::The target type classname (e.g. String, int, byte[])" })
    public static final String BODY_AS = "bodyAs(type)";

    @Metadata(description = "Converts the body to a String and removes all line-breaks, so the string is in one line.",
              javaType = "String", label = "function",
              examples = { "${bodyOneLine} -> Hello World // when body is 'Hello\\nWorld'" })
    public static final String BODY_ONE_LINE = "bodyOneLine";

    @Metadata(description = "The message body class type", javaType = "Class", label = "core",
              examples = { "${bodyType} -> java.lang.String // when body is a String" })
    public static final String BODY_TYPE = "bodyType";

    @Metadata(description = "The Camel Context", label = "core,ognl", javaType = "Object")
    public static final String CAMEL_CONTEXT = "camelContext";

    @Metadata(description = "The name of the CamelContext", javaType = "String", label = "core",
              examples = { "${camelId} -> camel-1" })
    public static final String CAMEL_ID = "camelId";

    @Metadata(description = "Capitalizes the message body/expression as a String value (upper case every words)",
              javaType = "String", label = "string", displayName = "Capitalize String Values",
              examples = { "${capitalize('hello world')} -> Hello World" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String CAPITALIZE = "capitalize(exp)";

    @Metadata(description = "Converts the message body (or expression) to a floating number and return the ceil value (rounded up to nearest integer).",
              label = "number", javaType = "Integer", displayName = "Ceil Number",
              examples = { "${ceil(2.3)} -> 3", "${ceil(5.0)} -> 5" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String CEIL = "ceil(exp)";

    @Metadata(description = "Removes all existing attachments on the message.",
              label = "attachment", javaType = "Object")
    public static final String CLEAR_ATTACHMENTS = "clearAttachments";

    @Metadata(description = "The collate function iterates the message body and groups the data into sub lists of specified size. This can be used with the Splitter EIP to split a message body and group/batch the split sub message into a group of N sub lists.",
              label = "collection", javaType = "java.util.Iterator", displayName = "Group Message Body into Sub Lists",
              examples = { "${collate(3)} -> [[1,2,3],[4,5,6],[7]] // when body is [1,2,3,4,5,6,7]" },
              annotations = { "param=num:int:required::The group size for each sub list" })
    public static final String COLLATE = "collate(num)";

    @Metadata(description = "Performs a string concat using two expressions (message body as default) with optional separator",
              label = "string", javaType = "String", displayName = "Concat",
              examples = {
                      "${concat('Hello',' ','World')} -> Hello World",
                      "${concat(${header.first},' ',${header.last})} -> John Doe" },
              annotations = {
                      "param=exp:Object:required::First expression",
                      "param=exp:Object:required::Second expression",
                      "param=separator:String:optional::The separator between the two expressions" })
    public static final String CONCAT = "concat(exp,exp,separator)";

    @Metadata(description = "Converts the message body (or expression) to the specified type.", label = "core,ognl",
              displayName = "Convert To",
              examples = {
                      "${convertTo(${body},String)} -> 42 // converts body to String",
                      "${convertTo(${header.count},int)} -> 5" },
              annotations = {
                      "param=exp:Object:optional:body:The expression to convert",
                      "param=type:String:required::The target type classname" })
    public static final String CONVERT_TO = "convertTo(exp,type)";

    @Metadata(description = "Returns true if the message body (or expression) contains part of the text (ignore case)",
              label = "condition", javaType = "boolean",
              examples = {
                      "${contains('Hello World','world')} -> true // case insensitive",
                      "${contains(${body},'error')} -> false" },
              annotations = {
                      "param=exp:Object:optional:body:The expression to check",
                      "param=text:String:required::The text to search for" })
    public static final String CONTAINS = "contains(exp,text)";

    @Metadata(description = "Evaluates to a java.util.Date object. Supported commands are: `now` for current timestamp, `millis` for current timestamp in millis (unix epoch), `exchangeCreated` for the timestamp when the current exchange was created, `header.xxx` to use the Long/Date object in the header with the key xxx. `variable.xxx` to use the Long/Date in the variable with the key xxx. `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx. `file` for the last modified timestamp of the file (available with a File consumer). Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "date", javaType = "java.util.Date", displayName = "Parse Date",
              examples = {
                      "${date(now)} -> current Date object",
                      "${date(now-24h)} -> Date 24 hours ago",
                      "${date(exchangeCreated)} -> Date when exchange was created",
                      "${date(header.myDate)} -> Date from header myDate" },
              annotations = {
                      "param=command:String:required::The date command (now, millis, exchangeCreated, header.xxx, variable.xxx, file). Supports offsets like now-24h" })
    public static final String DATE = "date(command)";

    @Metadata(description = "Formats the date to a String using the given date pattern, and with support for timezone. Supported commands are: `now` for current timestamp, `exchangeCreated` for the timestamp when the current exchange was created, `header.xxx` to use the Long/Date object in the header with the key xxx. `variable.xxx` to use the Long/Date in the variable with the key xxx. `exchangeProperty.xxx` to use the Long/Date object in the exchange property with the key xxx. `file` for the last modified timestamp of the file (available with a File consumer). Command accepts offsets such as: `now-24h` or `header.xxx+1h` or even `now+1h30m-100`.",
              label = "date", javaType = "String", displayName = "Date Formatter",
              examples = {
                      "${date-with-timezone(now:UTC:yyyy-MM-dd)} -> 2025-01-15",
                      "${date-with-timezone(now:Europe/Paris:HH:mm)} -> 14:30",
                      "${date-with-timezone(header.myDate:UTC:yyyy-MM-dd'T'HH:mm:ss)} -> 2025-01-15T10:30:00" },
              annotations = {
                      "param=command:String:required::The date command (now, exchangeCreated, header.xxx, variable.xxx, file). Supports offsets",
                      "param=timezone:String:optional::The timezone (e.g. UTC, Europe/Paris, America/New_York)",
                      "param=pattern:String:optional::The date pattern using java.text.SimpleDateFormat syntax" })
    public static final String DATE_WITH_TIMEZONE = "date-with-timezone(command:timezone:pattern)";

    @Metadata(description = "Returns a set of all the values with duplicates removed",
              label = "collection", javaType = "Set", displayName = "Distinct Values",
              examples = { "${distinct(1,2,2,3,3)} -> [1,2,3]", "${distinct()} -> unique values from body list" },
              annotations = {
                      "param=val:Object:optional:body:Values to de-duplicate. When omitted uses the message body (list or array)" })
    public static final String DISTINCT = "distinct(val...)";

    @Deprecated
    @Metadata(description = "Creates a new empty object (decided by type). Use `string` to create an empty String. Use `list` to create an empty `java.util.ArrayList`. Use `map` to create an empty `java.util.LinkedHashMap`. Use `set` to create an empty `java.util.LinkedHashSet`.",
              label = "collection", javaType = "Object", displayName = "Create Empty Object")
    public static final String EMPTY = "empty(type)";

    @Metadata(description = "The OS environment variable with the given name", label = "other", javaType = "Object",
              displayName = "OS Environment Variable",
              examples = { "${env.HOME} -> /home/user", "${env.JAVA_HOME} -> /usr/lib/jvm/java-17" },
              annotations = { "param=name:String:required::The environment variable name" })
    public static final String ENV = "env.name";

    @Metadata(description = "The current exchange", javaType = "org.apache.camel.Exchange", label = "core,ognl")
    public static final String EXCHANGE = "exchange";

    @Metadata(description = "The exchange id", javaType = "String", label = "core",
              examples = { "${exchangeId} -> ID-myhost-1234-1234567890-0-1" })
    public static final String EXCHANGE_ID = "exchangeId";

    @Metadata(description = "The exchange property with the given name", label = "core,ognl", javaType = "Object",
              examples = { "${exchangeProperty.myProp} -> propertyValue" },
              annotations = { "param=name:String:required::The exchange property name" })
    public static final String EXCHANGE_PROPERTY = "exchangeProperty.name";

    @Metadata(description = "The exception object on the exchange (also from caught exceptions), is null if no exception present.",
              javaType = "java.lang.Exception", label = "core,ognl")
    public static final String EXCEPTION = "exception";

    @Metadata(description = "The exception message (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "core", displayName = "Exception Message",
              examples = { "${exception.message} -> Connection refused" })
    public static final String EXCEPTION_MESSAGE = "exception.message";

    @Metadata(description = "The exception stacktrace (also from caught exceptions), is null if no exception present.",
              javaType = "String", label = "core", displayName = "Exception Stacktrace")
    public static final String EXCEPTION_STACKTRACE = "exception.stackTrace";

    @Metadata(description = "Returns a List containing the values that satisfy the predicate function (returning true)",
              label = "collection", javaType = "List", displayName = "Filter Elements",
              examples = { "${filter(${body},'${body} > 5')} -> [6,7,8] // when body is [3,4,5,6,7,8]" },
              annotations = {
                      "param=exp:Object:optional:body:The input collection expression",
                      "param=fun:String:required::The predicate function that returns true/false" })
    public static final String FILTER = "filter(exp,fun)";

    @Metadata(description = "Returns a List containing the values returned by the function when applied to each value from the input expression",
              label = "collection", javaType = "List", displayName = "For Each call Function",
              annotations = {
                      "param=exp:Object:optional:body:The input collection expression",
                      "param=fun:String:required::The function to apply to each element" })
    public static final String FOR_EACH = "forEach(exp,fun)";

    @Metadata(description = "Converts the message body (or expression) to a floating number and return the floor value (rounded down to nearest integer).",
              label = "number", javaType = "Integer", displayName = "Floor Number",
              examples = { "${floor(2.7)} -> 2", "${floor(5.0)} -> 5" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String FLOOR = "floor(exp)";

    @Metadata(description = "The original route id where this exchange was created.", javaType = "String", label = "core",
              examples = { "${fromRouteId} -> myRoute" })
    public static final String FROM_ROUTE_ID = "fromRouteId";

    @Metadata(description = "Invokes a custom function with the given name using the message body (or expression) as input parameter.",
              label = "core", javaType = "Object", displayName = "Invoke Custom Function",
              annotations = {
                      "param=name:String:required::The function name (registered in the Camel context)",
                      "param=exp:Object:optional:body:The input expression. When omitted uses the message body" })
    public static final String FUNCTION = "function(name,exp)";

    @Metadata(description = "Returns a hashed value (string in hex decimal) of the message body/expression using JDK MessageDigest. The algorithm can be SHA-256 (default) or SHA3-256.",
              label = "other", javaType = "String", displayName = "Compute Hash Value",
              examples = {
                      "${hash('Hello')} -> 185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969",
                      "${hash(${body},SHA3-256)} -> hash using SHA3-256" },
              annotations = {
                      "param=exp:Object:optional:body:The expression to hash. When omitted uses the message body",
                      "param=algorithm:String:optional:SHA-256:The hash algorithm (SHA-256 or SHA3-256)" })
    public static final String HASH = "hash(exp,algorithm)";

    @Metadata(description = "The message header with the given name", label = "core,ognl", javaType = "Object",
              examples = { "${header.ContentType} -> application/json", "${header.CamelFileName} -> data.txt" },
              annotations = { "param=name:String:required::The header name" })
    public static final String HEADER = "header.name";

    @Metadata(description = "Converts the message header to the given type (classname).", label = "core",
              javaType = "Object",
              examples = { "${headerAs(count,int)} -> 5 // converts header to integer" },
              annotations = {
                      "param=key:String:required::The header name",
                      "param=type:String:required::The target type classname" })
    public static final String HEADER_AS = "headerAs(key,type)";

    @Metadata(description = "Returns all the message headers in a Map", label = "core", javaType = "java.util.Map",
              examples = { "${headers} -> {ContentType=application/json, CamelFileName=data.txt}" })
    public static final String HEADERS = "headers";

    @Metadata(description = "Returns the local hostname (may be empty if not possible to resolve).", javaType = "String",
              label = "other",
              examples = { "${hostName} -> myserver.local" })
    public static final String HOST_NAME = "hostName";

    @Metadata(description = "Cleans the HTML to remove unsafe links and JavaScripts from the message body (or expression)",
              javaType = "String", label = "html",
              annotations = {
                      "param=exp:Object:optional:body:The expression containing HTML. When omitted uses the message body" })
    public static final String HTML_CLEAN = "htmlClean(exp)";

    @Metadata(description = "Decodes the HTML as plain text (removing all HTML tags) that is also suitable as input for AI and LLMs",
              javaType = "String", label = "html",
              examples = { "${htmlDecode('<p>Hello <b>World</b></p>')} -> Hello World" },
              annotations = {
                      "param=exp:Object:optional:body:The expression containing HTML. When omitted uses the message body" })
    public static final String HTML_DECODE = "htmlDecode(exp)";

    @Metadata(description = "Parses the HTML as a JSoup object from the message body (or expression)",
              javaType = "org.jsoup.nodes.Document", label = "html",
              annotations = {
                      "param=exp:Object:optional:body:The expression containing HTML. When omitted uses the message body" })
    public static final String HTML_PARSE = "htmlParse(exp)";

    @Metadata(description = "The message id", javaType = "String", label = "core")
    public static final String ID = "id";

    @Metadata(description = "Evaluates the predicate and returns the value of trueExp or falseExp. This function is similar to the ternary operator in Java.",
              label = "condition", javaType = "Object", displayName = "If Then Else",
              examples = {
                      "${iif(${header.admin} == true,'Admin','User')} -> Admin // when header admin is true",
                      "${iif(${body} > 100,'high','low')} -> high // when body is 150" },
              annotations = {
                      "param=predicate:String:required::The predicate expression to evaluate",
                      "param=trueExp:Object:required::The expression to return when predicate is true",
                      "param=falseExp:Object:required::The expression to return when predicate is false" })
    public static final String IIF = "iif(predicate,trueExp,falseExp)";

    @Metadata(description = "Whether the message body (or expression) is alphabetic value (A..Z). For more advanced checks use the `regex` operator.",
              label = "condition", javaType = "boolean", displayName = "Is Alphabetic Value",
              examples = { "${isAlpha('Hello')} -> true", "${isAlpha('Hello123')} -> false" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String IS_ALPHA = "isAlpha(exp)";

    @Metadata(description = "Whether the message body (or expression) is alphanumeric value (A..Z0-9). For more advanced checks use the `regex` operator.",
              label = "condition", javaType = "boolean", displayName = "Is Alphabetic-Numeric Value",
              examples = {
                      "${isAlphaNumeric('Hello123')} -> true",
                      "${isAlphaNumeric('Hello 123')} -> false // space is not alphanumeric" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String IS_ALPHA_NUMERIC = "isAlphaNumeric(exp)";

    @Metadata(description = "Whether the message body (or expression) is null or empty (list/map types are tested if they have 0 elements).",
              label = "condition", javaType = "boolean", displayName = "Is Empty",
              examples = {
                      "${isEmpty(${body})} -> true // when body is null or empty string",
                      "${isEmpty(${header.name})} -> false // when header has a value" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String IS_EMPTY = "isEmpty(exp)";

    @Metadata(description = "Whether the message body (or expression) is numeric value (0..9). For more advanced checks use the `regex` operator.",
              label = "condition", javaType = "boolean", displayName = "Is Numeric Value",
              examples = { "${isNumeric('12345')} -> true", "${isNumeric('12.34')} -> false // dot is not numeric" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String IS_NUMERIC = "isNumeric(exp)";

    @Metadata(description = "When working with JSon data, then this allows using the JQ language, for example, to extract data from the message body (in JSon format). This requires having camel-jq JAR on the classpath. For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "json", javaType = "Object", displayName = "JQ",
              examples = {
                      "${jq('.name')} -> John // extracts name field from JSON body",
                      "${jq(header:myHeader,'.items[0]')} -> first item from JSON in header" },
              annotations = {
                      "param=input:String:optional:body:The input source (header:key, exchangeProperty:key, variable:key). When omitted uses the message body",
                      "param=exp:String:required::The JQ expression" })
    public static final String JQ = "jq(input,exp)";

    @Metadata(description = "When working with JSon data, then this allows using the JSonPath language, for example, to extract data from the message body (in JSon format). This requires having camel-jsonpath JAR on the classpath. For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "json", javaType = "Object", displayName = "JSonPath",
              examples = {
                      "${jsonpath('$.name')} -> John // extracts name from JSON body",
                      "${jsonpath(header:myHeader,'$.items[0].id')} -> first item id from JSON in header" },
              annotations = {
                      "param=input:String:optional:body:The input source (header:key, exchangeProperty:key, variable:key). When omitted uses the message body",
                      "param=exp:String:required::The JSonPath expression" })
    public static final String JSONPATH = "jsonpath(input,exp)";

    @Metadata(description = "The join function iterates the message body/expression and joins the data into a string. The separator is by default a comma. The prefix is optional. The join uses the message body as source by default. It is possible to refer to another source (simple language) such as a header via the exp parameter. For example `join('&','id=','$\\{header.ids}')`.",
              label = "string", javaType = "String",
              examples = {
                      "${join(',')} -> A,B,C // when body is list [A,B,C]",
                      "${join('&','id=',${header.ids})} -> id=1&id=2&id=3" },
              annotations = {
                      "param=separator:String:optional:,:The separator between values",
                      "param=prefix:String:optional::The prefix for each value",
                      "param=exp:Object:optional:body:The input collection. When omitted uses the message body" })
    public static final String JOIN = "join(separator,prefix,exp)";

    @Metadata(description = "The payload length (number of bytes) of the message body (or expression).", label = "core",
              javaType = "int", displayName = "Length",
              examples = { "${length('Hello')} -> 5", "${length(${body})} -> payload length of the body" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String LENGTH = "length(exp)";

    @Metadata(description = "The list function creates an ArrayList with the given set of values.", label = "collection",
              javaType = "java.util.ArrayList", displayName = "Create List of values",
              examples = { "${list(1,2,3)} -> [1, 2, 3]", "${list('A','B','C')} -> [A, B, C]" },
              annotations = { "param=val:Object:required::The values to include in the list" })
    public static final String LIST = "list(val...)";

    @Metadata(description = "Adds the result of the expression to the message body (or expression) which is a list",
              label = "collection", javaType = "List", displayName = "List Add",
              annotations = {
                      "param=source:Object:optional:body:The source list. When omitted uses the message body",
                      "param=exp:Object:required::The value to add to the list" })
    public static final String LIST_ADD = "listAdd(source,exp)";

    @Metadata(description = "Removes the result of the expression from the message body (or expression) which is a list",
              label = "collection", javaType = "List", displayName = "List Remove",
              annotations = {
                      "param=source:Object:optional:body:The source list. When omitted uses the message body",
                      "param=exp:Object:required::The value to remove from the list" })
    public static final String LIST_REMOVE = "listRemove(source,exp)";

    @Metadata(description = "Loads the content of the resource from classpath (cannot load from file-system to avoid dangerous situations).",
              label = "core", javaType = "String", displayName = "Load",
              examples = { "${load(classpath:myTemplate.txt)} -> content of the file" },
              annotations = { "param=file:String:required::The classpath resource to load (e.g. classpath:myTemplate.txt)" })
    public static final String LOAD = "load(file)";

    @Metadata(description = "Lowercases the message body (or expression)", label = "string", javaType = "String",
              displayName = "Lowercase",
              examples = { "${lowercase('Hello World')} -> hello world" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String LOWERCASE = "lowercase(exp)";

    @Metadata(description = "Converts the message body to the given type (classname). If the body is null then the function will fail with an exception",
              label = "core,ognl", javaType = "Object",
              examples = { "${mandatoryBodyAs(String)} -> body as string // throws if body is null" },
              annotations = { "param=type:String:required::The target type classname (e.g. String, int, byte[])" })
    public static final String MANDATORY_BODY_AS = "mandatoryBodyAs(type)";

    @Metadata(description = "The map function creates a LinkedHashMap with the given set of pairs.", label = "collection",
              javaType = "java.util.LinkedHashMap", displayName = "Create Map of pairs",
              examples = { "${map('name','John','age','30')} -> {name=John, age=30}" },
              annotations = {
                      "param=key1:Object:required::The first key",
                      "param=value1:Object:required::The first value" })
    public static final String MAP = "map(key1,value1,...)";

    @Metadata(description = "Adds the result of the expression to the message body (or expression) which is a map",
              label = "collection", javaType = "Map", displayName = "Map Add",
              annotations = {
                      "param=source:Object:optional:body:The source map. When omitted uses the message body",
                      "param=key:Object:required::The key to add",
                      "param=exp:Object:required::The value expression to add" })
    public static final String MAP_ADD = "mapAdd(source,key,exp)";

    @Metadata(description = "Removes the result of the expression from the message body (or expression) which is a map",
              label = "collection", javaType = "Map", displayName = "Map Remove",
              annotations = {
                      "param=source:Object:optional:body:The source map. When omitted uses the message body",
                      "param=key:Object:required::The key to remove" })
    public static final String MAP_REMOVE = "mapRemove(source,key)";

    @Metadata(description = "Returns the maximum number from all the values (integral numbers only).", label = "number",
              javaType = "long", displayName = "Maximum Number",
              examples = { "${max(5,10,3)} -> 10", "${max()} -> 10 // when body is list [5,10,3]" },
              annotations = {
                      "param=val:long:optional:body:Values to compare. When omitted uses the message body (list or array)" })
    public static final String MAX = "max(val...)";

    @Metadata(description = "Converts the message to the given type (classname).", label = "core,ognl", javaType = "Object",
              annotations = { "param=type:String:required::The target type classname" })
    public static final String MESSAGE_AS = "messageAs(type)";

    @Metadata(description = "The message history of the current exchange (how it has been routed). This is similar to the route stack-trace message history the error handler logs in case of an unhandled exception. The boolean can be used to turn off detailed information to be less verbose, and avoid printing sensitive data from the message.",
              label = "core", javaType = "String", displayName = "Print Message History",
              examples = {
                      "${messageHistory()} -> detailed message history", "${messageHistory(false)} -> brief message history" },
              annotations = { "param=boolean:boolean:optional:true:Whether to include detailed information (headers, body)" })
    public static final String MESSAGE_HISTORY = "messageHistory(boolean)";

    @Metadata(description = "The message timestamp (millis since epoc) that this message originates from. Some systems like JMS, Kafka, AWS have a timestamp on the event/message that Camel received. This method returns the timestamp if a timestamp exists. The message timestamp and exchange created are different. An exchange always has a created timestamp which is the local timestamp when Camel created the exchange. The message timestamp is only available in some Camel components when the consumer is able to extract the timestamp from the source event. If the message has no timestamp, then 0 is returned.",
              javaType = "long", label = "core",
              examples = { "${messageTimestamp} -> 1705312200000 // millis since epoch, or 0 if not available" })
    public static final String MESSAGE_TIMESTAMP = "messageTimestamp";

    @Metadata(description = "Returns the minimum number from all the values (integral numbers only).", label = "number",
              javaType = "long", displayName = "Minimum Number",
              examples = { "${min(5,10,3)} -> 3", "${min()} -> 3 // when body is list [5,10,3]" },
              annotations = {
                      "param=val:long:optional:body:Values to compare. When omitted uses the message body (list or array)" })
    public static final String MIN = "min(val...)";

    @Metadata(description = "Creates a new empty object (decided by type). Use `string` to create an empty String. Use `list` to create an empty `java.util.ArrayList`. Use `map` to create an empty `java.util.LinkedHashMap`. Use `set` to create an empty `java.util.LinkedHashSet`.",
              label = "collection", javaType = "Object", displayName = "Create Empty Object",
              examples = {
                      "${newEmpty(list)} -> [] // empty ArrayList",
                      "${newEmpty(map)} -> {} // empty LinkedHashMap",
                      "${newEmpty(string)} -> empty string" },
              annotations = { "param=type:String:required::The type to create: string, list, map, or set" })
    public static final String NEW_EMPTY = "newEmpty(type)";

    @Metadata(description = "Normalizes the whitespace in the message body (or expression) by cleaning up excess whitespaces.",
              label = "string", javaType = "String", displayName = "Normalize Whitespace",
              examples = { "${normalizeWhitespace('Hello   World')} -> Hello World" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String NORMALIZE_WHITESPACE = "normalizeWhitespace(exp)";

    @Metadata(description = "Evaluates the predicate and returns the opposite.", label = "condition", javaType = "boolean")
    public static final String NOT = "not";

    @Metadata(description = "Returns a null value", label = "other", javaType = "Object",
              examples = { "${null} -> null" })
    public static final String NULL = "null";

    @Metadata(description = "The original incoming body (only available if allowUseOriginalMessage=true).", javaType = "Object",
              label = "core")
    public static final String ORIGINAL_BODY = "originalBody";

    @Metadata(description = "Pads the expression with extra padding if necessary, according the the total width. The separator is by default a space. If the width is negative then padding to the right, otherwise to the left.",
              label = "string", javaType = "String", displayName = "Pad String",
              examples = {
                      "${pad('Hi',10)} ->         Hi // left-padded with spaces to width 10",
                      "${pad('Hi',-10)} -> Hi         // right-padded with spaces",
                      "${pad('42',5,'0')} -> 00042 // left-padded with zeros" },
              annotations = {
                      "param=exp:Object:required::The expression to pad",
                      "param=width:int:required::The target width. Negative for right-padding",
                      "param=separator:String:optional: :The padding character (default is space)" })
    public static final String PAD = "pad(exp,width,separator)";

    @Metadata(description = "Converts the expression to a String, and attempts to pretty print if JSon or XML, otherwise the expression is returned as the String value.",
              label = "json,xml", javaType = "String", displayName = "Pretty Print",
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String PRETTY = "pretty(exp)";

    @Metadata(description = "Converts the body to a String, and attempts to pretty print if JSon or XML; otherwise the body is returned as the String value.",
              javaType = "String", label = "json,xml")
    public static final String PRETTY_BODY = "prettyBody";

    @Metadata(description = "Sorts the message body or expression in natural order",
              label = "collection", javaType = "List", displayName = "Sort",
              examples = {
                      "${sort()} -> [1,2,3] // when body is [3,1,2]",
                      "${sort(,true)} -> [3,2,1] // reverse order" },
              annotations = {
                      "param=exp:Object:optional:body:The expression. When omitted uses the message body",
                      "param=reverse:boolean:optional:false:Whether to sort in reverse order" })
    public static final String SORT = "sort(exp,reverse)";

    @Metadata(description = "Converts the expression to JSon String representation.",
              label = "json", javaType = "String", displayName = "To Pretty JSon",
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String TO_PRETTY_JSON = "toPrettyJson(exp)";

    @Metadata(description = "Converts the body to JSon String representation.",
              javaType = "String", label = "json", displayName = "To Pretty JSon Body")
    public static final String TO_PRETTY_JSON_BODY = "toPrettyJsonBody";

    @Metadata(description = "Converts the expression to JSon String representation.",
              label = "json", javaType = "String", displayName = "To JSon",
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String TO_JSON = "toJson(exp)";

    @Metadata(description = "Converts the body to JSon String representation.",
              javaType = "String", label = "json", displayName = "To JSon Body")
    public static final String TO_JSON_BODY = "toJsonBody";

    @Metadata(description = "Lookup a property placeholder with the given key. If the key does not exist nor has a value, then an optional default value can be specified.",
              label = "core", javaType = "String", displayName = "Property Placeholder",
              examples = {
                      "${properties:myApp.port} -> 8080",
                      "${properties:myApp.port:9090} -> 9090 // uses default when key not found" },
              annotations = {
                      "param=key:String:required::The property key to look up",
                      "param=default:String:optional::The default value if the key is not found" })
    public static final String PROPERTIES = "properties:key:default";

    @Metadata(description = "Checks whether a property placeholder with the given key exists or not. The result can be negated by prefixing the key with !",
              label = "core", javaType = "boolean", displayName = "Property Placeholder Exists",
              examples = {
                      "${propertiesExist:myApp.port} -> true // when property exists",
                      "${propertiesExist:!myApp.port} -> false // negated check" },
              annotations = { "param=key:String:required::The property key to check. Prefix with ! to negate" })
    public static final String PROPERTIES_EXIST = "propertiesExist:key";

    @Metadata(description = "Returns the message body (or expression) as a double quoted string",
              label = "string", javaType = "String",
              examples = { "${quote('Hello')} -> \"Hello\"" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String QUOTE = "quote(exp)";

    @Metadata(description = "Returns a random number between min and max (exclusive)",
              label = "number", javaType = "int",
              examples = {
                      "${random(1,100)} -> 42 // random number between 1 and 99",
                      "${random(0,10)} -> 7" },
              annotations = {
                      "param=min:int:required::The minimum value (inclusive)",
                      "param=max:int:required::The maximum value (exclusive)" })
    public static final String RANDOM = "random(min,max)";

    @Metadata(description = "Returns a list of increasing integers between the given interval (exclusive)",
              label = "number", javaType = "List",
              examples = { "${range(1,5)} -> [1, 2, 3, 4]" },
              annotations = {
                      "param=min:int:required::The start value (inclusive)",
                      "param=max:int:required::The end value (exclusive)" })
    public static final String RANGE = "range(min,max)";

    @Metadata(description = "To look up a bean from the Registry with the given name.", label = "core", javaType = "Object",
              displayName = "Bean By Id",
              examples = { "${ref:myDataSource} -> the bean from registry" },
              annotations = { "param=name:String:required::The bean name in the registry" })
    public static final String REF = "ref:name";

    @Metadata(description = "Replace all the string values in the message body/expression. To make it easier to replace single and double quotes, then you can use XML escaped values `\\&quot;` as double quote, `\\&apos;` as single quote, and `\\&empty;` as empty value.",
              label = "string", javaType = "String", displayName = "Replace String Values",
              examples = {
                      "${replace('-','_','hello-world')} -> hello_world",
                      "${replace(' ','_')} -> hello_world // when body is 'hello world'" },
              annotations = {
                      "param=from:String:required::The text to search for",
                      "param=to:String:required::The replacement text",
                      "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String REPLACE = "replace(from,to,exp)";

    @Metadata(description = "Returns a list of all the values, but in reverse order",
              label = "collection", javaType = "List", displayName = "Reverse Values",
              examples = { "${reverse(1,2,3)} -> [3,2,1]", "${reverse()} -> reversed list from body" },
              annotations = { "param=val:Object:optional:body:Values to reverse. When omitted uses the message body" })
    public static final String REVERSE = "reverse(val...)";

    @Metadata(description = "The route group of the current route the Exchange is being routed. Not all routes have a group assigned, so this may be null.",
              javaType = "String", label = "core")
    public static final String ROUTE_GROUP = "routeGroup";

    @Metadata(description = "The route id of the current route the Exchange is being routed", javaType = "String",
              label = "core",
              examples = { "${routeId} -> myRoute" })
    public static final String ROUTE_ID = "routeId";

    @Metadata(description = "Returns the message body (or expression) safely quoted if needed",
              label = "string", javaType = "String",
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String SAFE_QUOTE = "safeQuote(exp)";

    @Metadata(description = "Sets an attachment with payload from the message body/expression.",
              label = "attachment", javaType = "Object")
    public static final String SET_ATTACHMENT = "setVariable(key,exp)";

    @Metadata(description = "Sets a message header with the given expression (optional converting to the given type)",
              label = "core", javaType = "Object",
              examples = {
                      "${setHeader(myKey,${body})} -> sets header myKey to body value",
                      "${setHeader(count,int,${body})} -> sets header count as integer" },
              annotations = {
                      "param=name:String:required::The header name to set",
                      "param=type:String:optional::The type to convert the value to",
                      "param=exp:Object:required::The expression value to set" })
    public static final String SET_HEADER = "setHeader(name,type,exp)";

    @Metadata(description = "Sets a variable with the given expression (optional converting to the given type)",
              label = "core", javaType = "Object",
              examples = {
                      "${setVariable(myVar,${body})} -> sets variable myVar to body value",
                      "${setVariable(count,int,${body})} -> sets variable count as integer" },
              annotations = {
                      "param=name:String:required::The variable name to set",
                      "param=type:String:optional::The type to convert the value to",
                      "param=exp:Object:required::The expression value to set" })
    public static final String SET_VARIABLE = "setVariable(name,type,exp)";

    @Metadata(description = "Returns a list of all the values shuffled in random order",
              label = "collection", javaType = "List", displayName = "Shuffle Values",
              annotations = { "param=val:Object:optional:body:Values to shuffle. When omitted uses the message body" })
    public static final String SHUFFLE = "shuffle(val...)";

    @Metadata(description = "When working with JSon data, then this allows using the Simple JSonPath language, for example, to extract data from the message body (in JSon format). For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the JSon payload instead of the message body.",
              label = "json", javaType = "Object", displayName = "Simple JSonPath",
              examples = { "${simpleJsonpath('$.name')} -> John // extracts name from JSON body" },
              annotations = {
                      "param=input:String:optional:body:The input source (header:key, exchangeProperty:key, variable:key). When omitted uses the message body",
                      "param=exp:String:required::The Simple JSonPath expression" })
    public static final String SIMPLE_JSONPATH = "simpleJsonpath(input,exp)";

    @Metadata(description = "Returns the number of elements in collection or array based payloads. If the value is null then 0 is returned, otherwise 1.",
              label = "collection", javaType = "int", displayName = "Size",
              examples = {
                      "${size(${body})} -> 3 // when body is a list of 3 elements",
                      "${size()} -> 3 // same using body by default" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String SIZE = "size(exp)";

    @Metadata(description = "The skip function iterates the message body and skips the first number of items. This can be used with the Splitter EIP to split a message body and skip the first N number of items.",
              label = "collection", javaType = "java.util.Iterator", displayName = "Skip First Items from the Message Body",
              examples = { "${skip(2)} -> [3,4,5] // when body is [1,2,3,4,5]" },
              annotations = { "param=num:int:required::The number of items to skip from the beginning" })
    public static final String SKIP = "skip(num)";

    @Metadata(description = "Splits the message body/expression as a String value using the separator into a String array",
              label = "collection", javaType = "String[]", displayName = "Split String Values",
              examples = {
                      "${split(',')} -> [A, B, C] // when body is 'A,B,C'",
                      "${split('Hello-World','-')} -> [Hello, World]" },
              annotations = {
                      "param=exp:Object:optional:body:The expression. When omitted uses the message body",
                      "param=separator:String:optional:,:The separator to split on" })
    public static final String SPLIT = "split(exp,separator)";

    @Metadata(description = "Returns the id of the current step the Exchange is being routed.", javaType = "String",
              label = "core")
    public static final String STEP_ID = "stepId";

    @Metadata(description = "Returns a substring of the message body/expression. If only one positive number, then the returned string is clipped from the beginning. If only one negative number, then the returned string is clipped from the beginning. Otherwise the returned string is clipped between the head and tail positions.",
              label = "string", javaType = "String",
              examples = {
                      "${substring(0,5)} -> Hello // when body is 'Hello World'",
                      "${substring(6)} -> World // from position 6 to end",
                      "${substring(-5)} -> World // last 5 characters" },
              annotations = {
                      "param=head:int:required::The start position (inclusive). Negative counts from end",
                      "param=tail:int:optional::The end position (exclusive)" })
    public static final String SUBSTRING = "substring(head,tail)";

    @Metadata(description = "Returns a substring of the message body/expression that comes after. Returns null if nothing comes after.",
              label = "string", javaType = "String", displayName = "Substring After",
              examples = { "${substringAfter('Hello-World','-')} -> World" },
              annotations = {
                      "param=exp:Object:optional:body:The expression. When omitted uses the message body",
                      "param=before:String:required::The delimiter to search for" })
    public static final String SUBSTRING_AFTER = "substringAfter(exp,before)";

    @Metadata(description = "Returns a substring of the message body/expression that comes before. Returns null if nothing comes before.",
              label = "string", javaType = "String", displayName = "Substring Before",
              examples = { "${substringBefore('Hello-World','-')} -> Hello" },
              annotations = {
                      "param=exp:Object:optional:body:The expression. When omitted uses the message body",
                      "param=before:String:required::The delimiter to search for" })
    public static final String SUBSTRING_BEFORE = "substringBefore(exp,before)";

    @Metadata(description = "Returns a substring of the message body/expression that are between after and before. Returns null if nothing comes between.",
              label = "string", javaType = "String",
              examples = { "${substringBetween('[Hello]','[',']')} -> Hello" },
              annotations = {
                      "param=exp:Object:optional:body:The expression. When omitted uses the message body",
                      "param=after:String:required::The start delimiter",
                      "param=before:String:required::The end delimiter" })
    public static final String SUBSTRING_BETWEEN = "substringBetween(exp,after,before)";

    @Metadata(description = "Sums together all the values as integral numbers. This function can also be used to subtract by using negative numbers.",
              label = "number", javaType = "long", displayName = "Calculate Sum Number",
              examples = { "${sum(10,20,30)} -> 60", "${sum()} -> 60 // when body is list [10,20,30]" },
              annotations = {
                      "param=val:long:optional:body:Values to sum. When omitted uses the message body (list or array)" })
    public static final String SUM = "sum(val...)";

    @Metadata(description = "The JVM system property with the given name", label = "other", javaType = "Object",
              displayName = "JVM System Property",
              examples = { "${sys.java.version} -> 17.0.2", "${sys.user.home} -> /home/user" },
              annotations = { "param=name:String:required::The system property name" })
    public static final String SYS = "sys.name";

    @Metadata(description = "Returns the id of the current thread. Can be used for logging.", javaType = "long",
              label = "other")
    public static final String THREAD_ID = "threadId";

    @Metadata(description = "Returns the name of the current thread. Can be used for logging.", javaType = "String",
              label = "other")
    public static final String THREAD_NAME = "threadName";

    @Metadata(description = "Deliberately throws an error. Uses IllegalArgumentException by default if no type is specified (use fully qualified classname).",
              javaType = "java.lang.Exception", label = "core",
              examples = {
                      "${throwException('Something went wrong')} -> throws IllegalArgumentException",
                      "${throwException(java.io.IOException,'File not found')} -> throws IOException" },
              annotations = {
                      "param=type:String:optional:java.lang.IllegalArgumentException:The fully qualified exception classname",
                      "param=msg:String:required::The error message" })
    public static final String THROW_EXCEPTION = "throwException(type,msg)";

    @Metadata(description = "The trim function trims the message body (or expression) by removing all leading and trailing white spaces.",
              label = "string", javaType = "String", displayName = "Trim",
              examples = { "${trim('  Hello  ')} -> Hello" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String TRIM = "trim(exp)";

    @Metadata(description = "To refer to a type or field by its classname. To refer to a field, you can append .FIELD_NAME. For example, you can refer to the constant field from Exchange as: `org.apache.camel.Exchange.FILE_NAME`",
              label = "core", javaType = "Object", displayName = "Java Field Value",
              examples = {
                      "${type:org.apache.camel.Exchange.FILE_NAME} -> CamelFileName",
                      "${type:java.lang.Integer.MAX_VALUE} -> 2147483647" },
              annotations = {
                      "param=name:String:required::The fully qualified classname",
                      "param=field:String:optional::The field name on the class" })
    public static final String TYPE = "type:name.field";

    @Metadata(description = "What kind of type is the value (null,number,string,boolean,array,object)",
              label = "core", javaType = "String", displayName = "Kind of Type",
              examples = {
                      "${kindOfType('Hello')} -> string", "${kindOfType(123)} -> number",
                      "${kindOfType(${null})} -> null" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String KIND_OF_TYPE = "kindOfType(exp)";

    @Metadata(description = "Returns the message body (or expression) with any leading/ending quotes removed",
              label = "string", javaType = "String",
              examples = { "${unquote('\"Hello\"')} -> Hello" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String UNQUOTE = "unquote(exp)";

    @Metadata(description = "Uppercases the message body (or expression)", label = "string", javaType = "String",
              displayName = "Uppercase",
              examples = { "${uppercase('hello world')} -> HELLO WORLD" },
              annotations = { "param=exp:Object:optional:body:The expression. When omitted uses the message body" })
    public static final String UPPERCASE = "uppercase(exp)";

    @Metadata(description = "Returns a UUID using the Camel `UuidGenerator`. You can choose between `default`, `classic`, `short` and `simple` as the type. If no type is given, the default is used. It is also possible to use a custom `UuidGenerator` and bind the bean to the Registry with an id. For example `${uuid(myGenerator)}` where the ID is _myGenerator_.",
              label = "other", javaType = "String", displayName = "Generate UUID",
              examples = {
                      "${uuid()} -> ID-myhost-1234567890-0-1 // default generator",
                      "${uuid(short)} -> a1b2c3d4 // short format",
                      "${uuid(simple)} -> 550e8400-e29b-41d4-a716-446655440000 // simple UUID" },
              annotations = {
                      "param=type:String:optional:default:The UUID type: default, classic, short, simple, or a custom generator bean name" })
    public static final String UUID = "uuid(type)";

    @Metadata(description = "Returns the expression as a constant value",
              label = "core", javaType = "Object", displayName = "Value",
              examples = { "${val(42)} -> 42", "${val('Hello')} -> Hello" },
              annotations = { "param=exp:Object:required::The constant value expression" })
    public static final String VAL = "val(exp)";

    @Metadata(description = "The variable with the given name", label = "core,ognl", javaType = "Object",
              examples = { "${variable.myVar} -> variable value" },
              annotations = { "param=name:String:required::The variable name" })
    public static final String VARIABLE = "variable.name";

    @Metadata(description = "Converts the variable to the given type (classname).", label = "core", javaType = "Object",
              examples = { "${variableAs(count,int)} -> 5 // converts variable to integer" },
              annotations = {
                      "param=key:String:required::The variable name",
                      "param=type:String:required::The target type classname" })
    public static final String VARIABLE_AS = "variableAs(key,type)";

    @Metadata(description = "Returns all the variables from the current Exchange in a Map", label = "core",
              javaType = "java.util.Map",
              examples = { "${variables} -> {myVar=value1, count=5}" })
    public static final String VARIABLES = "variables";

    @Metadata(description = "When working with XML data, then this allows using the XPath language, for example, to extract data from the message body (in XML format). This requires having camel-xpath JAR on the classpath. For input (optional), you can choose `header:key`, `exchangeProperty:key` or `variable:key` to use as input for the XML payload instead of the message body.",
              label = "xml", javaType = "Object", displayName = "XPath",
              examples = {
                      "${xpath('/order/id/text()')} -> 123 // extracts id from XML body",
                      "${xpath(header:myXml,'/root/name/text()')} -> value from XML in header" },
              annotations = {
                      "param=input:String:optional:body:The input source (header:key, exchangeProperty:key, variable:key). When omitted uses the message body",
                      "param=exp:String:required::The XPath expression" })
    public static final String XPATH = "xpath(input,exp)";
}
