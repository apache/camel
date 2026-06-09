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

// ============================================================================
// Apache Camel Simple Language Validator
//
// A self-contained JavaScript validator for the Camel Simple expression
// language. Works in any browser or Node.js — no Java backend needed.
//
// The catalog data (FUNCTIONS, OPERATORS) is AUTO-GENERATED from simple.json.
// A future Maven generator will regenerate this section on each build.
// ============================================================================

// === AUTO-GENERATED CATALOG DATA — do not edit by hand ===

const FUNCTIONS = {
  "abs": [{"name":"abs(exp)","displayName":"Absolute Number","description":"Converts the message body (or expression) to a long number and return the absolute value.","group":"number","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "assert": [{"name":"assert(exp,msg)","displayName":"Assert","description":"Evaluates the expression and throws an exception with the message if the condition is false","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"msg","required":false}]}],
  "attachment": [{"name":"attachment.name","displayName":"Attachment","description":"The named attachment on the message.","group":"attachment","ognl":false,"deprecated":false}],
  "attachments": [{"name":"attachments","displayName":"Attachments","description":"The attachments on the message as a java.util.Map.","group":"attachment","ognl":false,"deprecated":false}],
  "attachmentsContent": [{"name":"attachmentsContent.name","displayName":"Attachments Content","description":"The named attachment content (as byte array) on the message.","group":"attachment","ognl":false,"deprecated":false}],
  "attachmentsContentAs": [{"name":"attachmentsContentAs(name,type)","displayName":"Attachments Content As","description":"The named attachment content (as given type) on the message.","group":"attachment","ognl":false,"deprecated":false,"params":[{"name":"name","required":true},{"name":"type","required":true}]}],
  "attachmentsContentAsText": [{"name":"attachmentsContentAsText.name","displayName":"Attachments Content As Text","description":"The named attachment content (as text/String) on the message.","group":"attachment","ognl":false,"deprecated":false}],
  "attachmentsHeader": [{"name":"attachmentsHeader(key,name)","displayName":"Attachments Header","description":"The named attachment header value on the message.","group":"attachment","ognl":false,"deprecated":false,"params":[{"name":"key","required":true},{"name":"name","required":true}]}],
  "attachmentsHeaderAs": [{"name":"attachmentsHeaderAs(key,name,type)","displayName":"Attachments Header As","description":"The named attachment header value (as given type) on the message.","group":"attachment","ognl":false,"deprecated":false,"params":[{"name":"key","required":true},{"name":"name","required":true},{"name":"type","required":true}]}],
  "attachmentsKeys": [{"name":"attachmentsKeys","displayName":"Attachments Keys","description":"The list of attachment keys on the message.","group":"attachment","ognl":false,"deprecated":false}],
  "attachmentsSize": [{"name":"attachmentsSize","displayName":"Attachments Size","description":"The number of attachments on the message.","group":"attachment","ognl":false,"deprecated":false}],
  "average": [{"name":"average(val...)","displayName":"Average","description":"Returns the average value of the given set of values","group":"number","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "base64Decode": [{"name":"base64Decode(exp)","displayName":"Base64 Decode","description":"Decodes the message body (or expression) using Base64 encoding.","group":"encode","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "base64Encode": [{"name":"base64Encode(exp)","displayName":"Base64 Encode","description":"Encodes the message body (or expression) using Base64 encoding.","group":"encode","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "bean": [{"name":"bean(name.method)","displayName":"Bean","description":"Invokes a method on a bean registered in the Camel registry.","group":"bean","ognl":false,"deprecated":false,"params":[{"name":"name.method","required":true}]}],
  "body": [{"name":"body","displayName":"Body","description":"The message body.","group":"message","ognl":true,"deprecated":false}],
  "bodyAs": [{"name":"bodyAs(type)","displayName":"Body As","description":"Converts the message body to a given type.","group":"message","ognl":true,"deprecated":false,"params":[{"name":"type","required":true}]}],
  "bodyOneLine": [{"name":"bodyOneLine","displayName":"Body One Line","description":"Converts the body to a single line (no line separators).","group":"message","ognl":false,"deprecated":false}],
  "bodyType": [{"name":"bodyType","displayName":"Body Type","description":"The class name of the message body.","group":"message","ognl":false,"deprecated":false}],
  "camelContext": [{"name":"camelContext","displayName":"Camel Context","description":"The CamelContext.","group":"context","ognl":true,"deprecated":false}],
  "camelId": [{"name":"camelId","displayName":"Camel Id","description":"The CamelContext name.","group":"context","ognl":false,"deprecated":false}],
  "capitalize": [{"name":"capitalize(exp)","displayName":"Capitalize","description":"Capitalizes the message body (or expression). First character is made uppercase, the rest lowercase.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "ceil": [{"name":"ceil(exp)","displayName":"Ceil","description":"Rounds the number up to the nearest integer.","group":"number","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "clearAttachments": [{"name":"clearAttachments","displayName":"Clear Attachments","description":"Clears all attachments from the message.","group":"attachment","ognl":false,"deprecated":false}],
  "collate": [{"name":"collate(num)","displayName":"Collate","description":"Splits the message body into groups of N.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"num","required":true}]}],
  "concat": [{"name":"concat(exp,exp,separator)","displayName":"Concat","description":"Concatenates two expressions with an optional separator.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"exp","required":true},{"name":"separator","required":false}]}],
  "contains": [{"name":"contains(exp,text)","displayName":"Contains","description":"Checks if the expression contains the given text.","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"text","required":true}]}],
  "convertTo": [{"name":"convertTo(exp,type)","displayName":"Convert To","description":"Converts the message body (or expression) to a given type.","group":"message","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"type","required":true}]}],
  "date": [{"name":"date(command)","displayName":"Date","description":"Evaluates a date expression. Commands: now, exchangeCreated, header.xxx.","group":"date","ognl":false,"deprecated":false,"params":[{"name":"command","required":true}]}],
  "date-with-timezone": [{"name":"date-with-timezone(command:timezone:pattern)","displayName":"Date With Timezone","description":"Evaluates a date expression with timezone. Commands: now, exchangeCreated, header.xxx.","group":"date","ognl":false,"deprecated":false,"params":[{"name":"command","required":true},{"name":"timezone","required":true},{"name":"pattern","required":true}]}],
  "distinct": [{"name":"distinct(val...)","displayName":"Distinct","description":"Returns a list of distinct values from the given set of values","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "empty": [{"name":"empty(type)","displayName":"Empty Value","description":"Returns an empty value of the given type. Types: string, list, map.","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"type","required":true}]}],
  "env": [{"name":"env.name","displayName":"Environment Variable","description":"The OS environment variable with the given name.","group":"env","ognl":false,"deprecated":false}],
  "exception": [{"name":"exception","displayName":"Exception","description":"The exchange exception (if any).","group":"message","ognl":true,"deprecated":false},{"name":"exception.message","displayName":"Exception Message","description":"The exchange exception message (if any). Will fallback to grab from caught exception.","group":"message","ognl":false,"deprecated":false},{"name":"exception.stackTrace","displayName":"Exception Stacktrace","description":"The exchange exception stacktrace (if any). Will fallback to grab from caught exception.","group":"message","ognl":false,"deprecated":false}],
  "exchange": [{"name":"exchange","displayName":"Exchange","description":"The exchange.","group":"message","ognl":true,"deprecated":false}],
  "exchangeId": [{"name":"exchangeId","displayName":"Exchange Id","description":"The exchange id.","group":"message","ognl":false,"deprecated":false}],
  "exchangeProperty": [{"name":"exchangeProperty.name","displayName":"Exchange Property","description":"The exchange property with the given name.","group":"message","ognl":true,"deprecated":false}],
  "filter": [{"name":"filter(exp,fun)","displayName":"Filter","description":"Filters a list by applying a predicate function to each element.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"fun","required":true}]}],
  "floor": [{"name":"floor(exp)","displayName":"Floor","description":"Rounds the number down to the nearest integer.","group":"number","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "forEach": [{"name":"forEach(exp,fun)","displayName":"For Each","description":"Evaluates a function for each element in a list and returns the results.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"fun","required":true}]}],
  "fromRouteId": [{"name":"fromRouteId","displayName":"From Route Id","description":"The original route id where this exchange was created.","group":"route","ognl":false,"deprecated":false}],
  "function": [{"name":"function(name,exp)","displayName":"Function","description":"Calls a custom function by name.","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"name","required":true},{"name":"exp","required":false}]}],
  "hash": [{"name":"hash(exp,algorithm)","displayName":"Hash","description":"Computes a hash of the message body (or expression). Algorithms: MD5, SHA-256, SHA-512.","group":"encode","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false},{"name":"algorithm","required":false}]}],
  "header": [{"name":"header.name","displayName":"Header","description":"The message header with the given name.","group":"message","ognl":true,"deprecated":false}],
  "headerAs": [{"name":"headerAs(key,type)","displayName":"Header As","description":"Converts the message header with the given name to a given type.","group":"message","ognl":true,"deprecated":false,"params":[{"name":"key","required":true},{"name":"type","required":true}]}],
  "headers": [{"name":"headers","displayName":"Headers","description":"The message headers as a java.util.Map.","group":"message","ognl":false,"deprecated":false}],
  "hostName": [{"name":"hostName","displayName":"Host Name","description":"The hostname of the local machine.","group":"env","ognl":false,"deprecated":false}],
  "htmlClean": [{"name":"htmlClean(exp)","displayName":"Html Clean","description":"Cleans HTML tags from the message body (or expression), leaving only text.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "htmlDecode": [{"name":"htmlDecode(exp)","displayName":"Html Decode","description":"Decodes HTML entities from the message body (or expression).","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "htmlParse": [{"name":"htmlParse(exp)","displayName":"Html Parse","description":"Parses HTML from the message body (or expression) and returns a jsoup Document.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "id": [{"name":"id","displayName":"Id","description":"The message id.","group":"message","ognl":false,"deprecated":false}],
  "iif": [{"name":"iif(predicate,trueExp,falseExp)","displayName":"Iif","description":"Inline if-then-else: evaluates the predicate and returns trueExp or falseExp.","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"predicate","required":true},{"name":"trueExp","required":true},{"name":"falseExp","required":false}]}],
  "isAlpha": [{"name":"isAlpha(exp)","displayName":"Is Alpha","description":"Checks if the expression contains only alphabetic characters.","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "isAlphaNumeric": [{"name":"isAlphaNumeric(exp)","displayName":"Is Alpha Numeric","description":"Checks if the expression contains only alphanumeric characters.","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "isEmpty": [{"name":"isEmpty(exp)","displayName":"Is Empty","description":"Checks if the expression is null or empty.","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "isNumeric": [{"name":"isNumeric(exp)","displayName":"Is Numeric","description":"Checks if the expression contains only numeric characters.","group":"condition","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "join": [{"name":"join(separator,prefix,exp)","displayName":"Join","description":"Joins the message body (or expression) using the given separator.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"separator","required":false},{"name":"prefix","required":false},{"name":"exp","required":false}]}],
  "jq": [{"name":"jq(input,exp)","displayName":"Jq","description":"Evaluates a JQ expression.","group":"script","ognl":false,"deprecated":false,"params":[{"name":"input","required":false},{"name":"exp","required":true}]}],
  "jsonpath": [{"name":"jsonpath(input,exp)","displayName":"Json Path","description":"Evaluates a JsonPath expression.","group":"script","ognl":false,"deprecated":false,"params":[{"name":"input","required":false},{"name":"exp","required":true}]}],
  "kindOfType": [{"name":"kindOfType(exp)","displayName":"Kind Of Type","description":"Returns the kind of type for the expression (null, boolean, int, long, float, double, string, array, map, object).","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "length": [{"name":"length(exp)","displayName":"Length","description":"Returns the length of the string, collection or array.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "list": [{"name":"list(val...)","displayName":"List","description":"Creates a new List from the given values.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "listAdd": [{"name":"listAdd(source,exp)","displayName":"List Add","description":"Adds an element to the list.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"source","required":true},{"name":"exp","required":true}]}],
  "listRemove": [{"name":"listRemove(source,exp)","displayName":"List Remove","description":"Removes an element from the list.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"source","required":true},{"name":"exp","required":true}]}],
  "load": [{"name":"load(file)","displayName":"Load","description":"Loads a file and returns its content as a String.","group":"file","ognl":false,"deprecated":false,"params":[{"name":"file","required":true}]}],
  "lowercase": [{"name":"lowercase(exp)","displayName":"Lowercase","description":"Converts the message body (or expression) to lowercase.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "mandatoryBodyAs": [{"name":"mandatoryBodyAs(type)","displayName":"Mandatory Body As","description":"Converts the message body to a given type (mandatory).","group":"message","ognl":true,"deprecated":false,"params":[{"name":"type","required":true}]}],
  "map": [{"name":"map(key1,value1,...)","displayName":"Map","description":"Creates a new Map from the given key-value pairs.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"key1","required":true},{"name":"value1","required":true}]}],
  "mapAdd": [{"name":"mapAdd(source,key,exp)","displayName":"Map Add","description":"Adds a key-value pair to the map.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"source","required":true},{"name":"key","required":true},{"name":"exp","required":true}]}],
  "mapRemove": [{"name":"mapRemove(source,key)","displayName":"Map Remove","description":"Removes a key from the map.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"source","required":true},{"name":"key","required":true}]}],
  "max": [{"name":"max(val...)","displayName":"Max","description":"Returns the maximum value of the given set of values","group":"number","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "messageAs": [{"name":"messageAs(type)","displayName":"Message As","description":"Converts the message to a given type.","group":"message","ognl":false,"deprecated":false,"params":[{"name":"type","required":true}]}],
  "messageHistory": [{"name":"messageHistory(boolean)","displayName":"Message History","description":"The message exchange history. Includes or excludes exchange properties.","group":"message","ognl":false,"deprecated":false,"params":[{"name":"boolean","required":false}]}],
  "messageTimestamp": [{"name":"messageTimestamp","displayName":"Message Timestamp","description":"The message timestamp.","group":"message","ognl":false,"deprecated":false}],
  "min": [{"name":"min(val...)","displayName":"Min","description":"Returns the minimum value of the given set of values","group":"number","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "newEmpty": [{"name":"newEmpty(type)","displayName":"New Empty","description":"Creates a new empty value of the given type. Types: string, list, map.","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"type","required":true}]}],
  "normalizeWhitespace": [{"name":"normalizeWhitespace(exp)","displayName":"Normalize Whitespace","description":"Normalizes whitespace in the message body (or expression).","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "not": [{"name":"not","displayName":"Not","description":"Negates the boolean value of the message body or expression.","group":"condition","ognl":false,"deprecated":false}],
  "null": [{"name":"null","displayName":"Null","description":"Represents a null value.","group":"misc","ognl":false,"deprecated":false}],
  "originalBody": [{"name":"originalBody","displayName":"Original Body","description":"The original incoming body (before any change by Camel routes).","group":"message","ognl":false,"deprecated":false}],
  "pad": [{"name":"pad(exp,width,separator)","displayName":"Pad","description":"Pads the message body (or expression) to a given width.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"width","required":true},{"name":"separator","required":false}]}],
  "pretty": [{"name":"pretty(exp)","displayName":"Pretty","description":"Pretty-prints the message body (or expression) if it is JSON or XML.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "prettyBody": [{"name":"prettyBody","displayName":"Pretty Body","description":"Pretty-prints the message body if it is JSON or XML.","group":"string","ognl":false,"deprecated":false}],
  "properties": [{"name":"properties:key:default","displayName":"Properties","description":"Lookup a property from the Camel properties component.","group":"env","ognl":false,"deprecated":false}],
  "propertiesExist": [{"name":"propertiesExist:key","displayName":"Properties Exist","description":"Checks whether a property exists.","group":"env","ognl":false,"deprecated":false}],
  "quote": [{"name":"quote(exp)","displayName":"Quote","description":"Wraps the message body (or expression) in double quotes.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "random": [{"name":"random(min,max)","displayName":"Random","description":"Returns a random integer between min (inclusive) and max (exclusive).","group":"number","ognl":false,"deprecated":false,"params":[{"name":"min","required":false},{"name":"max","required":true}]}],
  "range": [{"name":"range(min,max)","displayName":"Range","description":"Creates a list of integers from min to max (exclusive).","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"min","required":true},{"name":"max","required":true}]}],
  "ref": [{"name":"ref:name","displayName":"Ref","description":"Lookup a bean from the registry by name.","group":"bean","ognl":true,"deprecated":false}],
  "replace": [{"name":"replace(from,to,exp)","displayName":"Replace","description":"Replaces all occurrences of 'from' with 'to' in the message body (or expression).","group":"string","ognl":false,"deprecated":false,"params":[{"name":"from","required":true},{"name":"to","required":true},{"name":"exp","required":false}]}],
  "reverse": [{"name":"reverse(val...)","displayName":"Reverse","description":"Reverses the given list of values","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "routeGroup": [{"name":"routeGroup","displayName":"Route Group","description":"The route group of the current route.","group":"route","ognl":false,"deprecated":false}],
  "routeId": [{"name":"routeId","displayName":"Route Id","description":"The route id of the current route.","group":"route","ognl":false,"deprecated":false}],
  "safeQuote": [{"name":"safeQuote(exp)","displayName":"Safe Quote","description":"Wraps the message body (or expression) in double quotes, escaping inner quotes.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "setHeader": [{"name":"setHeader(name,type,exp)","displayName":"Set Header","description":"Sets a message header with the given name and value.","group":"message","ognl":false,"deprecated":false,"params":[{"name":"name","required":true},{"name":"type","required":false},{"name":"exp","required":true}]}],
  "setVariable": [{"name":"setVariable(key,exp)","displayName":"Set Variable","description":"Sets a variable with the given key and value.","group":"message","ognl":false,"deprecated":false,"params":[{"name":"key","required":true},{"name":"exp","required":true}]},{"name":"setVariable(name,type,exp)","displayName":"Set Variable","description":"Sets a variable with the given name, type and value.","group":"message","ognl":false,"deprecated":false,"params":[{"name":"name","required":true},{"name":"type","required":false},{"name":"exp","required":true}]}],
  "shuffle": [{"name":"shuffle(val...)","displayName":"Shuffle","description":"Shuffles the given list of values randomly","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "simpleJsonpath": [{"name":"simpleJsonpath(input,exp)","displayName":"Simple Jsonpath","description":"Evaluates a Simple JsonPath expression (simplified syntax).","group":"script","ognl":false,"deprecated":false,"params":[{"name":"input","required":false},{"name":"exp","required":true}]}],
  "size": [{"name":"size(exp)","displayName":"Size","description":"Returns the size of the collection, map, or string.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "skip": [{"name":"skip(num)","displayName":"Skip","description":"Skips the first N elements in the message body (assumed to be a collection).","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"num","required":true}]}],
  "sort": [{"name":"sort(exp,reverse)","displayName":"Sort","description":"Sorts the message body (or expression). Reverse=true for descending.","group":"collection","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false},{"name":"reverse","required":false}]}],
  "split": [{"name":"split(exp,separator)","displayName":"Split","description":"Splits the message body (or expression) by separator.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false},{"name":"separator","required":false}]}],
  "stepId": [{"name":"stepId","displayName":"Step Id","description":"The step id of the current step.","group":"route","ognl":false,"deprecated":false}],
  "substring": [{"name":"substring(head,tail)","displayName":"Substring","description":"Returns a substring of the message body. head=N takes the first N chars, tail=N takes the last N.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"head","required":false},{"name":"tail","required":false}]}],
  "substringAfter": [{"name":"substringAfter(exp,before)","displayName":"Substring After","description":"Returns the substring after the first occurrence of the delimiter.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"before","required":true}]}],
  "substringBefore": [{"name":"substringBefore(exp,before)","displayName":"Substring Before","description":"Returns the substring before the first occurrence of the delimiter.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"before","required":true}]}],
  "substringBetween": [{"name":"substringBetween(exp,after,before)","displayName":"Substring Between","description":"Returns the substring between two delimiters.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true},{"name":"after","required":true},{"name":"before","required":true}]}],
  "sum": [{"name":"sum(val...)","displayName":"Sum","description":"Returns the sum of the given set of values","group":"number","ognl":false,"deprecated":false,"params":[{"name":"val...","required":true}]}],
  "sys": [{"name":"sys.name","displayName":"System Property","description":"The JVM system property with the given name.","group":"env","ognl":false,"deprecated":false}],
  "threadId": [{"name":"threadId","displayName":"Thread Id","description":"The current thread id.","group":"env","ognl":false,"deprecated":false}],
  "threadName": [{"name":"threadName","displayName":"Thread Name","description":"The current thread name.","group":"env","ognl":false,"deprecated":false}],
  "throwException": [{"name":"throwException(type,msg)","displayName":"Throw Exception","description":"Throws an exception with the given type and message.","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"type","required":true},{"name":"msg","required":true}]}],
  "toJson": [{"name":"toJson(exp)","displayName":"To Json","description":"Converts the message body (or expression) to JSON string.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "toJsonBody": [{"name":"toJsonBody","displayName":"To Json Body","description":"Converts the message body to JSON string.","group":"string","ognl":false,"deprecated":false}],
  "toPrettyJson": [{"name":"toPrettyJson(exp)","displayName":"To Pretty Json","description":"Converts the message body (or expression) to pretty-printed JSON string.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "toPrettyJsonBody": [{"name":"toPrettyJsonBody","displayName":"To Pretty Json Body","description":"Converts the message body to pretty-printed JSON string.","group":"string","ognl":false,"deprecated":false}],
  "trim": [{"name":"trim(exp)","displayName":"Trim","description":"Trims leading and trailing whitespace from the message body (or expression).","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "type": [{"name":"type:name.field","displayName":"Type","description":"Lookup a static field on a Java class. Example: type:java.lang.Boolean.TRUE.","group":"bean","ognl":false,"deprecated":false}],
  "unquote": [{"name":"unquote(exp)","displayName":"Unquote","description":"Removes surrounding double quotes from the message body (or expression).","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "uppercase": [{"name":"uppercase(exp)","displayName":"Uppercase","description":"Converts the message body (or expression) to uppercase.","group":"string","ognl":false,"deprecated":false,"params":[{"name":"exp","required":false}]}],
  "uuid": [{"name":"uuid(type)","displayName":"Uuid","description":"Returns a UUID. Types: default, classic, short, simple, custom.","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"type","required":false}]}],
  "val": [{"name":"val(exp)","displayName":"Val","description":"Evaluates the expression as a value (no type conversion).","group":"misc","ognl":false,"deprecated":false,"params":[{"name":"exp","required":true}]}],
  "variable": [{"name":"variable.name","displayName":"Variable","description":"The exchange variable with the given name.","group":"message","ognl":true,"deprecated":false}],
  "variableAs": [{"name":"variableAs(key,type)","displayName":"Variable As","description":"Converts the exchange variable with the given key to a given type.","group":"message","ognl":true,"deprecated":false,"params":[{"name":"key","required":true},{"name":"type","required":true}]}],
  "variables": [{"name":"variables","displayName":"Variables","description":"The exchange variables as a java.util.Map.","group":"message","ognl":false,"deprecated":false}],
  "xpath": [{"name":"xpath(input,exp)","displayName":"Xpath","description":"Evaluates an XPath expression.","group":"script","ognl":false,"deprecated":false,"params":[{"name":"input","required":false},{"name":"exp","required":true}]}]
};

const OPERATORS = {
  "==":{"displayName":"Eq","description":"Tests equality between left and right operand values. Camel will coerce the right operand type to match the left.","kind":"binary","syntax":"LHS == RHS","precedence":10},
  "=~":{"displayName":"Eq ignore","description":"Tests equality between left and right operand values, ignoring case for string comparison.","kind":"binary","syntax":"LHS =~ RHS","precedence":10},
  ">":{"displayName":"Gt","description":"Tests whether the left operand is greater than the right operand.","kind":"binary","syntax":"LHS > RHS","precedence":10},
  ">=":{"displayName":"Gte","description":"Tests whether the left operand is greater than or equal to the right operand.","kind":"binary","syntax":"LHS >= RHS","precedence":10},
  "<":{"displayName":"Lt","description":"Tests whether the left operand is less than the right operand.","kind":"binary","syntax":"LHS < RHS","precedence":10},
  "<=":{"displayName":"Lte","description":"Tests whether the left operand is less than or equal to the right operand.","kind":"binary","syntax":"LHS <= RHS","precedence":10},
  "!=":{"displayName":"Not eq","description":"Tests inequality between left and right operand values.","kind":"binary","syntax":"LHS != RHS","precedence":10},
  "!=~":{"displayName":"Not eq ignore","description":"Tests inequality between left and right operand values, ignoring case for string comparison.","kind":"binary","syntax":"LHS !=~ RHS","precedence":10},
  "contains":{"displayName":"Contains","description":"Tests whether the left operand string contains the right operand string.","kind":"binary","syntax":"LHS contains RHS","precedence":10},
  "!contains":{"displayName":"Not contains","description":"Tests whether the left operand string does not contain the right operand string.","kind":"binary","syntax":"LHS !contains RHS","precedence":10},
  "~~":{"displayName":"Contains ignorecase","description":"Tests whether the left operand string contains the right operand string, ignoring case.","kind":"binary","syntax":"LHS ~~ RHS","precedence":10},
  "!~~":{"displayName":"Not contains ignorecase","description":"Tests whether the left operand string does not contain the right operand string, ignoring case.","kind":"binary","syntax":"LHS !~~ RHS","precedence":10},
  "regex":{"displayName":"Regex","description":"Tests whether the left operand matches the right operand as a regular expression.","kind":"binary","syntax":"LHS regex 'pattern'","precedence":10},
  "!regex":{"displayName":"Not regex","description":"Tests whether the left operand does not match the right operand as a regular expression.","kind":"binary","syntax":"LHS !regex 'pattern'","precedence":10},
  "in":{"displayName":"In","description":"Tests whether the left operand is in a set of comma-separated values.","kind":"binary","syntax":"LHS in 'val1,val2,...'","precedence":10},
  "!in":{"displayName":"Not in","description":"Tests whether the left operand is not in a set of comma-separated values.","kind":"binary","syntax":"LHS !in 'val1,val2,...'","precedence":10},
  "is":{"displayName":"Is","description":"Tests whether the left operand is an instance of the right operand type (Java classname or short name).","kind":"binary","syntax":"LHS is 'typeName'","precedence":10},
  "!is":{"displayName":"Not is","description":"Tests whether the left operand is not an instance of the right operand type.","kind":"binary","syntax":"LHS !is 'typeName'","precedence":10},
  "range":{"displayName":"Range","description":"Tests whether the left operand is within the numeric range specified by 'from..to'.","kind":"binary","syntax":"LHS range 'from..to'","precedence":10},
  "!range":{"displayName":"Not range","description":"Tests whether the left operand is not within the numeric range specified by 'from..to'.","kind":"binary","syntax":"LHS !range 'from..to'","precedence":10},
  "startsWith":{"displayName":"Starts with","description":"Tests whether the left operand string starts with the right operand string.","kind":"binary","syntax":"LHS startsWith RHS","precedence":10},
  "!startsWith":{"displayName":"Not starts with","description":"Tests whether the left operand string does not start with the right operand string.","kind":"binary","syntax":"LHS !startsWith RHS","precedence":10},
  "endsWith":{"displayName":"Ends with","description":"Tests whether the left operand string ends with the right operand string.","kind":"binary","syntax":"LHS endsWith RHS","precedence":10},
  "!endsWith":{"displayName":"Not ends with","description":"Tests whether the left operand string does not end with the right operand string.","kind":"binary","syntax":"LHS !endsWith RHS","precedence":10},
  "++":{"displayName":"Inc","description":"Increments the numeric value by one. Must immediately follow a function closing brace.","kind":"unary","syntax":"${fn}++","precedence":1},
  "--":{"displayName":"Dec","description":"Decrements the numeric value by one. Must immediately follow a function closing brace.","kind":"unary","syntax":"${fn}--","precedence":1},
  "&&":{"displayName":"And","description":"Logical AND. Both left and right predicates must evaluate to true.","kind":"logical","syntax":"predicate && predicate","precedence":30},
  "||":{"displayName":"Or","description":"Logical OR. At least one of the left or right predicates must evaluate to true.","kind":"logical","syntax":"predicate || predicate","precedence":30},
  "? :":{"displayName":"Ternary","description":"Ternary conditional operator. Evaluates the predicate and returns trueValue if true, falseValue if false. Requires spaces around both ? and : tokens.","kind":"ternary","syntax":"predicate ? trueValue : falseValue","precedence":25},
  "~>":{"displayName":"Chain","description":"Pipes the result of the left expression as input body to the right expression. Use $param in the right expression to reference the piped value explicitly.","kind":"chain","syntax":"expr ~> expr","precedence":5},
  "?~>":{"displayName":"Chain null safe","description":"Null-safe chain operator. Same as ~> but stops chaining and returns null if the left expression evaluates to null.","kind":"chain","syntax":"expr ?~> expr","precedence":5},
  "?:":{"displayName":"Elvis","description":"Elvis operator (null-coalescing). Returns the left operand if it is not null/empty, otherwise returns the right operand as a fallback value.","kind":"other","syntax":"expr ?: defaultValue","precedence":20}
};

// === END AUTO-GENERATED CATALOG DATA ===

// ========================================================================
// Tokenizer
// ========================================================================

function tokenize(input) {
  const tokens = [];
  let i = 0;
  const len = input.length;

  while (i < len) {
    // Function start: ${ or $simple{
    if (input[i] === '$' && i + 1 < len && input[i + 1] === '{') {
      tokens.push({ type: 'functionStart', value: '${', start: i, end: i + 2 });
      i += 2;
      continue;
    }
    if (input.startsWith('$simple{', i)) {
      tokens.push({ type: 'functionStart', value: '$simple{', start: i, end: i + 8 });
      i += 8;
      continue;
    }

    // Function end
    if (input[i] === '}') {
      tokens.push({ type: 'functionEnd', value: '}', start: i, end: i + 1 });
      i += 1;
      continue;
    }

    // Escape sequences
    if (input[i] === '\\' && i + 1 < len) {
      const next = input[i + 1];
      tokens.push({ type: 'escape', value: '\\' + next, start: i, end: i + 2 });
      i += 2;
      continue;
    }

    // Single quote
    if (input[i] === "'") {
      tokens.push({ type: 'singleQuote', value: "'", start: i, end: i + 1 });
      i += 1;
      continue;
    }

    // Double quote
    if (input[i] === '"') {
      tokens.push({ type: 'doubleQuote', value: '"', start: i, end: i + 1 });
      i += 1;
      continue;
    }

    // Whitespace
    if (input[i] === ' ' || input[i] === '\t' || input[i] === '\n' || input[i] === '\r') {
      let start = i;
      while (i < len && (input[i] === ' ' || input[i] === '\t' || input[i] === '\n' || input[i] === '\r')) {
        i++;
      }
      tokens.push({ type: 'whitespace', value: input.substring(start, i), start, end: i });
      continue;
    }

    // Text (everything else — accumulated into runs)
    let start = i;
    while (i < len && input[i] !== '$' && input[i] !== '}' && input[i] !== '\\' &&
           input[i] !== "'" && input[i] !== '"' &&
           input[i] !== ' ' && input[i] !== '\t' && input[i] !== '\n' && input[i] !== '\r') {
      i++;
    }
    if (i > start) {
      tokens.push({ type: 'text', value: input.substring(start, i), start, end: i });
    }
  }

  return tokens;
}

// ========================================================================
// Validator
// ========================================================================

function findClosestFunction(name) {
  const names = Object.keys(FUNCTIONS);
  let best = null;
  let bestDist = Infinity;
  for (const fn of names) {
    const d = levenshtein(name.toLowerCase(), fn.toLowerCase());
    if (d < bestDist && d <= 3) {
      bestDist = d;
      best = fn;
    }
  }
  return best;
}

function levenshtein(a, b) {
  const m = a.length, n = b.length;
  if (m === 0) return n;
  if (n === 0) return m;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i-1] === b[j-1]
        ? dp[i-1][j-1]
        : 1 + Math.min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1]);
    }
  }
  return dp[m][n];
}

function extractBaseName(content) {
  let base = content;
  for (const ch of ['(', '.', ':']) {
    const pos = base.indexOf(ch);
    if (pos !== -1) {
      base = base.substring(0, pos);
    }
  }
  return base;
}

function isKnownOperator(word) {
  return word in OPERATORS;
}

function findBinaryOperator(text) {
  const binaryOps = Object.entries(OPERATORS)
    .filter(([, op]) => op.kind === 'binary')
    .map(([sym]) => sym)
    .sort((a, b) => b.length - a.length);
  for (const op of binaryOps) {
    if (text === op) return op;
  }
  return null;
}

function validate(input, mode) {
  if (mode === undefined) mode = 'expression';
  const diagnostics = [];
  const tokens = tokenize(input);

  // Track delimiter balancing
  let functionDepth = 0;
  const functionStarts = [];
  let inSingleQuote = false;
  let singleQuoteStart = -1;
  let inDoubleQuote = false;
  let doubleQuoteStart = -1;

  // Collect function contents for validation
  const functionBlocks = [];
  let currentFunctionStart = -1;
  let currentFunctionTokenStart = -1;

  for (let t = 0; t < tokens.length; t++) {
    const tok = tokens[t];

    if (tok.type === 'singleQuote' && !inDoubleQuote) {
      if (inSingleQuote) {
        inSingleQuote = false;
      } else {
        inSingleQuote = true;
        singleQuoteStart = tok.start;
      }
      continue;
    }

    if (tok.type === 'doubleQuote' && !inSingleQuote) {
      if (inDoubleQuote) {
        inDoubleQuote = false;
      } else {
        inDoubleQuote = true;
        doubleQuoteStart = tok.start;
      }
      continue;
    }

    // Inside quotes, only check for unmatched function braces
    if (inSingleQuote || inDoubleQuote) {
      if (tok.type === 'functionStart') {
        functionDepth++;
        functionStarts.push(tok.start);
        currentFunctionStart = tok.start;
        currentFunctionTokenStart = t;
      } else if (tok.type === 'functionEnd') {
        if (functionDepth > 0) {
          functionDepth--;
          const fnStart = functionStarts.pop();
          const fnContent = input.substring(fnStart + 2, tok.start);
          functionBlocks.push({ content: fnContent, start: fnStart, end: tok.end });
        }
      }
      continue;
    }

    if (tok.type === 'functionStart') {
      functionDepth++;
      functionStarts.push(tok.start);
      currentFunctionStart = tok.start;
      currentFunctionTokenStart = t;
    } else if (tok.type === 'functionEnd') {
      if (functionDepth > 0) {
        functionDepth--;
        const fnStart = functionStarts.pop();
        const fnContent = input.substring(fnStart + (input.startsWith('$simple{', fnStart) ? 8 : 2), tok.start);
        functionBlocks.push({ content: fnContent, start: fnStart, end: tok.end });
      } else {
        diagnostics.push({
          severity: 'error',
          message: "Unexpected '}' without matching '${' ",
          start: tok.start,
          end: tok.end
        });
      }
    }

    // Validate escape sequences
    if (tok.type === 'escape') {
      const escaped = tok.value[1];
      if (!['n', 't', 'r', '}', '\\', "'", '"'].includes(escaped)) {
        diagnostics.push({
          severity: 'warning',
          message: `Unusual escape sequence '\\${escaped}'`,
          start: tok.start,
          end: tok.end
        });
      }
    }
  }

  // Check for unclosed delimiters
  if (functionDepth > 0) {
    const unclosedStart = functionStarts[functionStarts.length - 1];
    diagnostics.push({
      severity: 'error',
      message: "'${' has no closing '}'",
      start: unclosedStart,
      end: unclosedStart + 2
    });
  }

  if (inSingleQuote) {
    diagnostics.push({
      severity: 'error',
      message: "Single quote has no closing quote",
      start: singleQuoteStart,
      end: singleQuoteStart + 1
    });
  }

  if (inDoubleQuote) {
    diagnostics.push({
      severity: 'error',
      message: "Double quote has no closing quote",
      start: doubleQuoteStart,
      end: doubleQuoteStart + 1
    });
  }

  // Validate function names
  for (const block of functionBlocks) {
    validateFunction(block.content, block.start, block.end, diagnostics);
  }

  // Validate predicate structure
  if (mode === 'predicate') {
    validatePredicate(input, tokens, diagnostics);
  }

  return {
    valid: diagnostics.filter(d => d.severity === 'error').length === 0,
    diagnostics
  };
}

function validateFunction(content, blockStart, blockEnd, diagnostics) {
  // Handle nested functions — only validate the outermost name
  const baseName = extractBaseName(content);

  if (!baseName || baseName.length === 0) {
    diagnostics.push({
      severity: 'error',
      message: "Empty function reference '${}' ",
      start: blockStart,
      end: blockEnd
    });
    return;
  }

  // Skip validation for contents that start with ${ (nested function as argument)
  if (baseName.startsWith('$')) {
    return;
  }

  // Check if function name is known
  if (!(baseName in FUNCTIONS)) {
    const suggestion = findClosestFunction(baseName);
    const msg = suggestion
      ? `Unknown function '${baseName}', did you mean '${suggestion}'?`
      : `Unknown function '${baseName}'`;
    diagnostics.push({
      severity: 'error',
      message: msg,
      start: blockStart,
      end: blockEnd,
      suggestion
    });
  }
}

function validatePredicate(input, tokens, diagnostics) {
  // Build a simplified structure: segments separated by operators
  // We look for operator tokens in the non-quoted, non-function text
  const segments = [];
  let current = '';
  let currentStart = 0;
  let depth = 0;
  let inQuote = false;
  let quoteChar = null;

  for (let i = 0; i < input.length; i++) {
    const ch = input[i];

    // Track quote state
    if ((ch === "'" || ch === '"') && depth === 0) {
      if (!inQuote) {
        inQuote = true;
        quoteChar = ch;
      } else if (ch === quoteChar) {
        inQuote = false;
        quoteChar = null;
      }
      current += ch;
      continue;
    }

    if (inQuote) {
      current += ch;
      continue;
    }

    // Track function depth
    if (ch === '$' && i + 1 < input.length && input[i + 1] === '{') {
      depth++;
      current += '${';
      i++;
      continue;
    }
    if (ch === '}') {
      if (depth > 0) depth--;
      current += '}';
      continue;
    }

    // Outside functions and quotes — check for operators
    if (depth === 0) {
      let rest = input.substring(i).trimStart();
      let skipWs = input.substring(i).length - rest.length;
      let opMatch = null;

      // Check all binary/logical operators (longest match first)
      const allOps = Object.entries(OPERATORS)
        .filter(([, op]) => op.kind === 'binary' || op.kind === 'logical')
        .map(([sym]) => sym)
        .sort((a, b) => b.length - a.length);

      for (const op of allOps) {
        if (rest.startsWith(op) && (rest.length === op.length || rest[op.length] === ' ')) {
          opMatch = op;
          break;
        }
      }

      if (opMatch && (ch === ' ' || current.trim().length === 0)) {
        // Found an operator — save the left segment
        if (current.trim().length > 0) {
          segments.push({ type: 'operand', value: current.trim(), start: currentStart });
        }
        const opStart = i + skipWs;
        segments.push({ type: 'operator', value: opMatch, start: opStart, kind: OPERATORS[opMatch].kind });
        i = opStart + opMatch.length;
        current = '';
        currentStart = i;
        continue;
      }
    }

    current += ch;
  }

  if (current.trim().length > 0) {
    segments.push({ type: 'operand', value: current.trim(), start: currentStart });
  }

  // Check for misspelled word operators between operands
  const wordOperators = Object.keys(OPERATORS).filter(op => /^[a-zA-Z!]/.test(op));
  for (let s = 0; s < segments.length; s++) {
    const seg = segments[s];
    if (seg.type === 'operand') {
      const words = seg.value.split(/\s+/);
      for (const word of words) {
        if (word.length >= 2 && /^!?[a-zA-Z]+$/.test(word) && !findBinaryOperator(word)) {
          for (const op of wordOperators) {
            if (levenshtein(word.toLowerCase(), op.toLowerCase()) <= 2 && word.toLowerCase() !== op.toLowerCase()) {
              diagnostics.push({
                severity: 'warning',
                message: `'${word}' looks like a misspelled operator, did you mean '${op}'?`,
                start: seg.start + seg.value.indexOf(word),
                end: seg.start + seg.value.indexOf(word) + word.length,
                suggestion: op
              });
              break;
            }
          }
        }
      }
    }
  }

  // Validate operator usage
  for (let s = 0; s < segments.length; s++) {
    const seg = segments[s];
    if (seg.type === 'operator') {
      const opInfo = OPERATORS[seg.value];
      if (opInfo && (opInfo.kind === 'binary' || opInfo.kind === 'logical')) {
        // Check for LHS
        const prev = s > 0 ? segments[s - 1] : null;
        if (!prev || prev.type !== 'operand') {
          diagnostics.push({
            severity: 'error',
            message: `Operator '${seg.value}' has no left-hand side operand`,
            start: seg.start,
            end: seg.start + seg.value.length
          });
        }
        // Check for RHS
        const next = s + 1 < segments.length ? segments[s + 1] : null;
        if (!next || next.type !== 'operand') {
          diagnostics.push({
            severity: 'error',
            message: `Operator '${seg.value}' has no right-hand side operand`,
            start: seg.start,
            end: seg.start + seg.value.length
          });
        }
      }
    }
  }
}

// ========================================================================
// Autocomplete
// ========================================================================

function complete(input, cursor) {
  const suggestions = [];

  // Find context at cursor position
  const before = input.substring(0, cursor);
  const lastDollarBrace = before.lastIndexOf('${');
  const lastCloseBrace = before.lastIndexOf('}');

  if (lastDollarBrace > lastCloseBrace) {
    // We are inside a ${...} — suggest function names
    const partial = before.substring(lastDollarBrace + 2);
    const basePart = extractBaseName(partial).toLowerCase();

    for (const [name, fns] of Object.entries(FUNCTIONS)) {
      if (name.toLowerCase().startsWith(basePart)) {
        for (const fn of fns) {
          suggestions.push({
            label: fn.name,
            displayName: fn.displayName,
            description: fn.description,
            group: fn.group,
            insertText: fn.name
          });
        }
      }
    }
  } else if (before.trimEnd().endsWith('}') || /\S$/.test(before)) {
    // After a function or operand — suggest operators
    for (const [sym, op] of Object.entries(OPERATORS)) {
      if (op.kind === 'binary' || op.kind === 'logical') {
        suggestions.push({
          label: sym,
          displayName: op.displayName,
          description: op.description,
          insertText: ' ' + sym + ' '
        });
      }
    }
  }

  return suggestions;
}

// ========================================================================
// Public API
// ========================================================================

function getFunctions() {
  return FUNCTIONS;
}

function getOperators() {
  return OPERATORS;
}

// ========================================================================
// Exports (works in both Node.js and browser)
// ========================================================================

const CamelSimpleValidator = { validate, complete, getFunctions, getOperators, tokenize };

if (typeof module !== 'undefined' && module.exports) {
  module.exports = CamelSimpleValidator;
}
if (typeof globalThis !== 'undefined') {
  globalThis.CamelSimpleValidator = CamelSimpleValidator;
}

// ========================================================================
// Self-test (run with: node camel-simple-validator.js)
// ========================================================================

if (typeof require !== 'undefined' && require.main === module) {
  let passed = 0;
  let failed = 0;

  function test(name, input, mode, expectValid, expectErrorSubstring) {
    const result = validate(input, mode);
    let ok = result.valid === expectValid;
    if (expectErrorSubstring) {
      const hasMatch = result.diagnostics.some(d => d.message.includes(expectErrorSubstring));
      ok = ok && hasMatch;
    }
    if (ok) {
      passed++;
    } else {
      failed++;
      console.log(`FAIL: ${name}`);
      console.log(`  input: ${JSON.stringify(input)}`);
      console.log(`  expected valid=${expectValid}` + (expectErrorSubstring ? `, error containing "${expectErrorSubstring}"` : ''));
      console.log(`  got valid=${result.valid}, diagnostics:`, JSON.stringify(result.diagnostics, null, 2));
    }
  }

  // === Valid expressions ===
  test('simple body', '${body}', 'expression', true);
  test('header access', '${header.foo}', 'expression', true);
  test('template text', 'Hello ${body}', 'expression', true);
  test('multiple functions', '${header.from} to ${header.to}', 'expression', true);
  test('nested function in text', "Hello ${header.name}, you have ${header.count} items", 'expression', true);
  test('exchangeId', '${exchangeId}', 'expression', true);
  test('function with parens', '${trim()}', 'expression', true);
  test('function with args', '${substring(1,3)}', 'expression', true);
  test('plain text', 'Hello World', 'expression', true);
  test('empty string', '', 'expression', true);
  test('env variable', '${env.HOME}', 'expression', true);
  test('sys property', '${sys.user.name}', 'expression', true);
  test('properties', '${properties:myKey}', 'expression', true);
  test('date function', '${date(now)}', 'expression', true);
  test('camelId', '${camelId}', 'expression', true);
  test('routeId', '${routeId}', 'expression', true);
  test('exchangeProperty', '${exchangeProperty.myProp}', 'expression', true);
  test('variable', '${variable.myVar}', 'expression', true);
  test('bodyAs', '${bodyAs(String)}', 'expression', true);
  test('uppercase', '${uppercase(${body})}', 'expression', true);
  test('escape newline', 'Hello\\nWorld', 'expression', true);
  test('escape tab', 'col1\\tcol2', 'expression', true);
  test('$simple prefix', '$simple{body}', 'expression', true);

  // === Valid predicates ===
  test('simple equality', "${body} == 'foo'", 'predicate', true);
  test('numeric comparison', '${header.count} > 5', 'predicate', true);
  test('contains', "${header.title} contains 'Camel'", 'predicate', true);
  test('logical AND', "${body} == 'foo' && ${header.bar} == 'baz'", 'predicate', true);
  test('logical OR', "${body} == 'foo' || ${body} == 'bar'", 'predicate', true);
  test('not equal', "${header.type} != 'test'", 'predicate', true);
  test('regex operator', "${header.code} regex '\\d{3}'", 'predicate', true);
  test('in operator', "${header.color} in 'red,blue,green'", 'predicate', true);
  test('range operator', "${header.age} range '18..65'", 'predicate', true);
  test('is operator', "${body} is 'String'", 'predicate', true);
  test('startsWith', "${header.name} startsWith 'Camel'", 'predicate', true);
  test('endsWith', "${header.file} endsWith '.xml'", 'predicate', true);
  test('case-insensitive contains', "${header.title} ~~ 'camel'", 'predicate', true);
  test('greater or equal', '${header.count} >= 10', 'predicate', true);
  test('less or equal', '${header.count} <= 100', 'predicate', true);

  // === Invalid expressions ===
  test('unclosed function', '${body', 'expression', false, "no closing '}'");
  test('extra closing brace', 'body}', 'expression', false, "Unexpected '}'");
  test('unknown function', '${boddy}', 'expression', false, "Unknown function 'boddy'");
  test('unknown function with suggestion', '${headr.foo}', 'expression', false, "did you mean 'header'");
  test('empty function', '${}', 'expression', false, "Empty function");
  test('unclosed single quote', "Hello '${body}", 'expression', false, "Single quote has no closing");
  test('unclosed double quote', 'Hello "${body}', 'expression', false, "Double quote has no closing");
  test('unknown function trim typo', '${trm()}', 'expression', false, "did you mean 'trim'");

  // === Invalid predicates ===
  test('operator no RHS', "${body} ==", 'predicate', false, "no right-hand side");
  test('operator no LHS', "== 'foo'", 'predicate', false, "no left-hand side");

  // === Predicate warnings ===
  test('misspelled operator', "${header.foo} contans 'bar'", 'predicate', true, "did you mean 'contains'");

  // === Summary ===
  console.log(`\n${passed + failed} tests: ${passed} passed, ${failed} failed`);
  if (failed > 0) {
    process.exit(1);
  }
}
