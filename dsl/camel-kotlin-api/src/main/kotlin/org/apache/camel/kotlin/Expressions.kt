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
package org.apache.camel.kotlin

import org.apache.camel.Expression
import org.apache.camel.builder.ValueBuilder
import org.apache.camel.model.PropertyDefinition
import org.apache.camel.model.language.*
import javax.xml.xpath.XPathFactory
import kotlin.reflect.KClass

fun Expression.which(): ValueBuilder {
    return ValueBuilder(this)
}

fun constant(constant: String, i: ConstantExpressionDsl.() -> Unit = {}): ConstantExpression {
    val def = ConstantExpression(constant)
    ConstantExpressionDsl(def).apply(i)
    return def
}

fun csimple(csimple: String, i: CSimpleExpressionDsl.() -> Unit = {}): CSimpleExpression {
    val def = CSimpleExpression(csimple)
    CSimpleExpressionDsl(def).apply(i)
    return def
}

fun datasonnet(datasonnet: String, i: DatasonnetExpressionDsl.() -> Unit = {}): DatasonnetExpression {
    val def = DatasonnetExpression(datasonnet)
    DatasonnetExpressionDsl(def).apply(i)
    return def
}

fun exchangeProperty(exchangeProperty: String): ExchangePropertyExpression {
    return ExchangePropertyExpression(exchangeProperty)
}

fun groovy(groovy: String, i: GroovyExpressionDsl.() -> Unit = {}): GroovyExpression {
    val def = GroovyExpression(groovy)
    GroovyExpressionDsl(def).apply(i)
    return def
}

fun header(header: String): HeaderExpression {
    return HeaderExpression(header)
}

fun hl7terser(hl7terser: String, i: Hl7TerserExpressionDsl.() -> Unit = {}): Hl7TerserExpression {
    val def = Hl7TerserExpression(hl7terser)
    Hl7TerserExpressionDsl(def).apply(i)
    return def
}

fun java(java: String, i: JavaExpressionDsl.() -> Unit = {}): JavaExpression {
    val def = JavaExpression(java)
    JavaExpressionDsl(def).apply(i)
    return def
}

fun js(js: String, i: JavaScriptExpressionDsl.() -> Unit = {}): JavaScriptExpression {
    val def = JavaScriptExpression(js)
    JavaScriptExpressionDsl(def).apply(i)
    return def
}

fun javaScript(javaScript: String): JavaScriptExpression {
    return js(javaScript)
}

fun jq(jq: String, i: JqExpressionDsl.() -> Unit): JqExpression {
    val def = JqExpression(jq)
    JqExpressionDsl(def).apply(i)
    return def
}

fun jsonPath(jsonPath: String, i: JsonPathExpressionDsl.() -> Unit = {}): JsonPathExpression {
    val def = JsonPathExpression(jsonPath)
    JsonPathExpressionDsl(def).apply(i)
    return def
}

fun language(language: String, expression: String): LanguageExpression {
    val def = LanguageExpression(language, expression)
    return def
}

fun method(i: MethodCallExpressionDsl.() -> Unit): MethodCallExpression {
    val def = MethodCallExpression()
    MethodCallExpressionDsl(def).apply(i)
    return def
}

fun mvel(mvel: String): MvelExpression {
    val def = MvelExpression(mvel)
    return def
}

fun ognl(ognl: String): OgnlExpression {
    val def = OgnlExpression(ognl)
    return def
}

fun python(python: String): PythonExpression {
    val def = PythonExpression(python)
    return def
}

fun ref(ref: String): RefExpression {
    val def = RefExpression(ref)
    return def
}

fun simple(simple: String): SimpleExpression {
    val def = SimpleExpression(simple)
    return def
}

fun spel(spel: String): SpELExpression {
    val def = SpELExpression(spel)
    return def
}

fun tokenize(tokenize: String, i: TokenizerExpressionDsl.() -> Unit = {}): TokenizerExpression {
    val def = TokenizerExpression(tokenize)
    TokenizerExpressionDsl(def).apply(i)
    return def
}

fun xtokenize(xtokenize: String, i: XMLTokenizerExpressionDsl.() -> Unit = {}): XMLTokenizerExpression {
    val def = XMLTokenizerExpression(xtokenize)
    XMLTokenizerExpressionDsl(def).apply(i)
    return def
}

fun xpath(xpath: String, i: XPathExpressionDsl.() -> Unit = {}): XPathExpression {
    val def = XPathExpression(xpath)
    XPathExpressionDsl(def).apply(i)
    return def
}

fun xquery(xquery: String): XQueryExpression {
    val def = XQueryExpression(xquery)
    return def
}

@CamelDslMarker
class ConstantExpressionDsl(
    val def: ConstantExpression
) : TypedExpressionDsl(def)

@CamelDslMarker
class CSimpleExpressionDsl(
    val def: CSimpleExpression
) : TypedExpressionDsl(def)

@CamelDslMarker
class DatasonnetExpressionDsl(
    val def: DatasonnetExpression
) : TypedExpressionDsl(def) {

    fun bodyMediaType(bodyMediaType: String) {
        def.bodyMediaType = bodyMediaType
    }

    fun outputMediaType(outputMediaType: String) {
        def.outputMediaType = outputMediaType
    }
}

@CamelDslMarker
class GroovyExpressionDsl(
    val def: GroovyExpression
) : TypedExpressionDsl(def)

@CamelDslMarker
class Hl7TerserExpressionDsl(
    val def: Hl7TerserExpression
) : SingleInputTypedExpressionDsl(def)

@CamelDslMarker
class JavaExpressionDsl(
    val def: JavaExpression
) : TypedExpressionDsl(def) {

    fun preCompile(preCompile: Boolean) {
        def.preCompile = preCompile.toString()
    }

    fun preCompile(preCompile: String) {
        def.preCompile = preCompile
    }

    fun singleQuotes(singleQuotes: Boolean) {
        def.singleQuotes = singleQuotes.toString()
    }

    fun singleQuotes(singleQuotes: String) {
        def.singleQuotes = singleQuotes
    }
}

@CamelDslMarker
class JavaScriptExpressionDsl(
    val def: JavaScriptExpression
) : TypedExpressionDsl(def)

@CamelDslMarker
class JqExpressionDsl(
    val def: JqExpression
) : SingleInputTypedExpressionDsl(def)

@CamelDslMarker
class JsonPathExpressionDsl(
    val def: JsonPathExpression
) : SingleInputTypedExpressionDsl(def) {

    fun suppressExceptions(suppressExceptions: Boolean) {
        def.suppressExceptions = suppressExceptions.toString()
    }

    fun suppressExceptions(suppressExceptions: String) {
        def.suppressExceptions = suppressExceptions
    }

    fun allowSimple(allowSimple: Boolean) {
        def.allowSimple = allowSimple.toString()
    }

    fun allowSimple(allowSimple: String) {
        def.allowSimple = allowSimple
    }

    fun allowEasyPredicate(allowEasyPredicate: Boolean) {
        def.allowEasyPredicate = allowEasyPredicate.toString()
    }

    fun allowEasyPredicate(allowEasyPredicate: String) {
        def.allowEasyPredicate = allowEasyPredicate
    }

    fun writeAsString(writeAsString: Boolean) {
        def.writeAsString = writeAsString.toString()
    }

    fun writeAsString(writeAsString: String) {
        def.writeAsString = writeAsString
    }

    fun unpackArray(unpackArray: Boolean) {
        def.unpackArray = unpackArray.toString()
    }

    fun unpackArray(unpackArray: String) {
        def.unpackArray = unpackArray
    }

    fun option(option: String) {
        def.option = option
    }
}

@CamelDslMarker
class MethodCallExpressionDsl(
    val def: MethodCallExpression
) : TypedExpressionDsl(def) {

    fun ref(ref: String) {
        def.ref = ref
    }

    fun method(method: String) {
        def.method = method
    }

    fun beanType(beanType: KClass<*>) {
        def.beanType = beanType.java
    }

    fun beanTypeName(beanTypeName: String) {
        def.beanTypeName = beanTypeName
    }

    fun scope(scope: String) {
        def.scope = scope
    }

    fun validate(validate: Boolean) {
        def.validate = validate.toString()
    }

    fun validate(validate: String) {
        def.validate = validate
    }

    fun instance(instance: Any) {
        def.instance = instance
    }
}

@CamelDslMarker
class TokenizerExpressionDsl(
    val def: TokenizerExpression
) : SingleInputExpressionDsl(def) {

    fun endToken(endToken: String) {
        def.endToken = endToken
    }

    fun regex(regex: String) {
        def.regex = regex
    }

    fun inheritNamespaceTagName(inheritNamespaceTagName: Boolean) {
        def.inheritNamespaceTagName = inheritNamespaceTagName.toString()
    }

    fun inheritNamespaceTagName(inheritNamespaceTagName: String) {
        def.inheritNamespaceTagName = inheritNamespaceTagName
    }

    fun xml(xml: Boolean) {
        def.xml = xml.toString()
    }

    fun xml(xml: String) {
        def.xml = xml
    }

    fun includeTokens(includeTokens: Boolean) {
        def.includeTokens = includeTokens.toString()
    }

    fun includeTokens(includeTokens: String) {
        def.includeTokens = includeTokens
    }

    fun group(group: String) {
        def.group = group
    }

    fun groupDelimiter(groupDelimiter: String) {
        def.groupDelimiter = groupDelimiter
    }

    fun skipFirst(skipFirst: Boolean) {
        def.skipFirst = skipFirst.toString()
    }

    fun skipFirst(skipFirst: String) {
        def.skipFirst = skipFirst
    }
}

class XMLTokenizerExpressionDsl(
    val def: XMLTokenizerExpression
) : NamespaceAwareExpressionDsl(def) {

    fun mode(mode: String) {
        def.mode = mode
    }

    fun group(group: Int) {
        def.group = group.toString()
    }

    fun group(group: String) {
        def.group = group
    }
}

class XPathExpressionDsl(
    val def: XPathExpression
) : NamespaceAwareExpressionDsl(def) {

    fun documentType(documentType: KClass<*>) {
        def.documentType = documentType.java
    }

    fun documentTypeName(documentTypeName: String) {
        def.documentTypeName = documentTypeName
    }

    fun resultType(resultType: KClass<*>) {
        def.resultType = resultType.java
    }

    fun resultTypeName(resultTypeName: String) {
        def.resultTypeName = resultTypeName
    }

    fun saxon(saxon: Boolean) {
        def.saxon = saxon.toString()
    }

    fun saxon(saxon: String) {
        def.saxon = saxon
    }

    fun factoryRef(factoryRef: String) {
        def.factoryRef = factoryRef
    }

    fun objectModel(objectModel: String) {
        def.objectModel = objectModel
    }

    fun logNamespaces(logNamespaces: Boolean) {
        def.logNamespaces = logNamespaces.toString()
    }

    fun logNamespaces(logNamespaces: String) {
        def.logNamespaces = logNamespaces
    }

    fun xpathFactory(xpathFactory: XPathFactory) {
        def.xPathFactory = xpathFactory
    }

    fun threadSafety(threadSafety: Boolean) {
        def.threadSafety = threadSafety.toString()
    }

    fun threadSafety(threadSafety: String) {
        def.threadSafety = threadSafety
    }

    fun preCompile(preCompile: Boolean) {
        def.preCompile = preCompile.toString()
    }

    fun preCompile(preCompile: String) {
        def.preCompile = preCompile
    }
}

@CamelDslMarker
class XQueryExpressionDsl(
    val def: XQueryExpression
): NamespaceAwareExpressionDsl(def) {

    fun type(type: String) {
        def.type = type
    }

    fun resultType(resultType: KClass<*>) {
        def.resultType = resultType.java
    }

    fun resultTypeName(resultTypeName: String) {
        def.resultTypeName = resultTypeName
    }

    fun configurationRef(configurationRef: String) {
        def.configurationRef = configurationRef
    }

    fun configuration(configuration: Any) {
        def.configuration = configuration
    }
}

@CamelDslMarker
abstract class ExpressionDsl(
    private val def: ExpressionDefinition
) {

    fun trim(trim: Boolean) {
        def.trim = trim.toString()
    }

    fun trim(trim: String) {
        def.trim = trim
    }
}

@CamelDslMarker
abstract class TypedExpressionDsl(
    private val def: TypedExpressionDefinition
) : ExpressionDsl(def) {

    fun resultType(resultType: KClass<*>) {
        def.resultType = resultType.java
    }
}

@CamelDslMarker
abstract class SingleInputTypedExpressionDsl(
    private val def: SingleInputTypedExpressionDefinition
) : TypedExpressionDsl(def) {

    fun headerName(headerName: String) {
        def.headerName = headerName
    }

    fun propertyName(propertyName: String) {
        def.propertyName = propertyName
    }

    fun variableName(variableName: String) {
        def.variableName = variableName
    }
}

@CamelDslMarker
abstract class SingleInputExpressionDsl(
    private val def: SingleInputExpressionDefinition
) : ExpressionDsl(def) {

    fun headerName(headerName: String) {
        def.headerName = headerName
    }

    fun propertyName(propertyName: String) {
        def.propertyName = propertyName
    }
}

@CamelDslMarker
abstract class NamespaceAwareExpressionDsl(
    private val def: NamespaceAwareExpression
) : SingleInputExpressionDsl(def) {

    fun namespaces(namespaces: Map<String, String>) {
        def.namespaces = namespaces
    }

    fun namespace(namespace: List<Pair<String, String>>) {
        def.namespace = namespace.map { PropertyDefinition(it.first, it.second) }
    }
}