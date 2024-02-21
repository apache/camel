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

import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.Expression
import org.apache.camel.Predicate
import org.apache.camel.kotlin.languages.constant
import org.apache.camel.kotlin.model.*
import org.apache.camel.model.*
import org.apache.camel.spi.DataType
import org.apache.camel.spi.Policy
import kotlin.reflect.KClass

@CamelDslMarker
class StepsDsl(
    val def: ProcessorDefinition<*>
) {

    fun aggregate(aggregate: Expression, i: AggregateDsl.() -> Unit) {
        val aggregateDef = def.aggregate(aggregate)
        AggregateDsl(aggregateDef).apply(i)
    }

    fun bean(i: BeanDsl.() -> Unit) {
        val beanDef = BeanDefinition()
        def.addOutput(beanDef)
        BeanDsl(beanDef).apply(i)
    }

    fun choice(i: ChoiceDsl.() -> Unit) {
        val choiceDef = def.choice()
        ChoiceDsl(choiceDef).apply(i)
        choiceDef.end()
    }

    fun circuitBreaker(i: CircuitBreakerDsl.() -> Unit) {
        val circuitBreakerDef = def.circuitBreaker()
        CircuitBreakerDsl(circuitBreakerDef).apply(i)
        circuitBreakerDef.end()
    }

    fun claimCheck(i: ClaimCheckDsl.() -> Unit) {
        val claimCheckDef = def.claimCheck()
        ClaimCheckDsl(claimCheckDef).apply(i)
    }

    fun convertBodyTo(convertBodyTo: KClass<*>, i: ConvertBodyDsl.() -> Unit = {}) {
        val convertBodyDef = ConvertBodyDefinition(convertBodyTo.java)
        def.addOutput(convertBodyDef)
        ConvertBodyDsl(convertBodyDef).apply(i)
    }

    fun convertBodyTo(convertBodyTo: String, i: ConvertBodyDsl.() -> Unit = {}) {
        val convertBodyDef = ConvertBodyDefinition(convertBodyTo)
        def.addOutput(convertBodyDef)
        ConvertBodyDsl(convertBodyDef).apply(i)
    }

    fun convertHeaderTo(convertHeaderTo: String, type: KClass<*>, i: ConvertHeaderDsl.() -> Unit = {}) {
        val convertHeaderDef = ConvertHeaderDefinition(convertHeaderTo, type.java)
        def.addOutput(convertHeaderDef)
        ConvertHeaderDsl(convertHeaderDef).apply(i)
    }

    fun convertHeaderTo(convertHeaderTo: String, type: String, i: ConvertHeaderDsl.() -> Unit = {}) {
        val convertHeaderDef = ConvertHeaderDefinition(convertHeaderTo, type)
        def.addOutput(convertHeaderDef)
        ConvertHeaderDsl(convertHeaderDef).apply(i)
    }

    fun convertVariableTo(convertVariableTo: String, type: KClass<*>, i: ConvertVariableDsl.() -> Unit = {}) {
        val convertVarDef = ConvertVariableDefinition(convertVariableTo, type.java)
        def.addOutput(convertVarDef)
        ConvertVariableDsl(convertVarDef).apply(i)
    }

    fun convertVariableTo(convertVariableTo: String, type: String, i: ConvertVariableDsl.() -> Unit = {}) {
        val convertVarDef = ConvertVariableDefinition(convertVariableTo, type)
        def.addOutput(convertVarDef)
        ConvertVariableDsl(convertVarDef).apply(i)
    }

    fun delay(delay: Long, i: DelayDsl.() -> Unit = {}) {
        val delayDef = def.delay(delay)
        DelayDsl(delayDef).apply(i)
    }

    fun delay(delay: Expression, i: DelayDsl.() -> Unit = {}) {
        val delayDef = def.delay(delay)
        DelayDsl(delayDef).apply(i)
    }

    fun dynamicRouter(expression: Expression, i: DynamicRouterDsl.() -> Unit) {
        val dynamicRouterDef = def.dynamicRouter(expression)
        DynamicRouterDsl(dynamicRouterDef).apply(i)
    }

    fun filter(filter: Predicate, i: FilterDsl.() -> Unit) {
        val filterDef = def.filter(filter)
        FilterDsl(filterDef).apply(i)
        filterDef.end()
    }

    fun enrich(i: EnrichDsl.() -> Unit) {
        val enrichDef = EnrichDefinition()
        def.addOutput(enrichDef)
        EnrichDsl(enrichDef).apply(i)
    }

    fun idempotentConsumer(idempotentConsumer: Expression, i: IdempotentConsumerDsl.() -> Unit) {
        val idempotentConsumerDef = def.idempotentConsumer(idempotentConsumer)
        IdempotentConsumerDsl(idempotentConsumerDef).apply(i)
    }

    fun loadBalance(i: LoadBalanceDsl.() -> Unit) {
        val loadBalanceDef = def.loadBalance()
        LoadBalanceDsl(loadBalanceDef).apply(i)
        loadBalanceDef.end()
    }

    fun log(i: LogDsl.() -> Unit) {
        val logDef = LogDefinition()
        def.addOutput(logDef)
        LogDsl(logDef).apply(i)
    }

    fun log(message: String, i: LogDsl.() -> Unit = {}) {
        val logDef = LogDefinition(message)
        def.addOutput(logDef)
        LogDsl(logDef).apply(i)
    }

    fun loop(loop: Expression, i: LoopDsl.() -> Unit = {}) {
        val loopDef = def.loop(loop)
        LoopDsl(loopDef).apply(i)
        loopDef.end()
    }

    fun loop(loop: Int, i: LoopDsl.() -> Unit = {}) {
        loop(constant(loop.toString()), i)
    }

    fun loopDoWhile(loop: Predicate, i: LoopDsl.() -> Unit = {}) {
        val loopDef = def.loopDoWhile(loop)
        LoopDsl(loopDef).apply(i)
        loopDef.end()
    }

    fun marshal(i: DataFormatDsl.() -> Unit) {
        val dsl = DataFormatDsl().apply(i)
        def.marshal(dsl.def)
    }

    fun multicast(i: MulticastDsl.() -> Unit) {
        val multicastDef = def.multicast()
        MulticastDsl(multicastDef).apply(i)
        multicastDef.end()
    }

    fun pausable(i: PausableDsl.() -> Unit) {
        val pausableDef = def.pausable()
        PausableDsl(pausableDef).apply(i)
    }

    fun pipeline(i: StepsDsl.() -> Unit) {
        val pipelineDef = def.pipeline()
        StepsDsl(pipelineDef).apply(i)
        pipelineDef.end()
    }

    fun policy(policy: String) {
        def.policy(policy)
    }

    fun policy(policy: Policy) {
        def.policy(policy)
    }

    fun pollEnrich(i: PollEnrichDsl.() -> Unit) {
        val pollEnrichDef = PollEnrichDefinition()
        PollEnrichDsl(pollEnrichDef).apply(i)
        def.addOutput(pollEnrichDef)
    }

    fun process(process: (Exchange) -> Unit) {
        def.process(process)
    }

    fun recipientList(recipientList: Expression, i: RecipientListDsl.() -> Unit) {
        val recipientListDef = def.recipientList(recipientList)
        RecipientListDsl(recipientListDef).apply(i)
    }

    fun removeHeader(removeHeader: String) {
        def.removeHeader(removeHeader)
    }

    fun removeHeaders(removeHeaders: String) {
        def.removeHeaders(removeHeaders)
    }

    fun removeHeaders(removeHeaders: String, vararg excludePatterns: String) {
        def.removeHeaders(removeHeaders, *excludePatterns)
    }

    fun removeProperties(removeProperties: String) {
        def.removeProperties(removeProperties)
    }

    fun removeProperties(removeProperties: String, vararg excludePatterns: String) {
        def.removeProperties(removeProperties, *excludePatterns)
    }

    fun removeProperty(removeProperty: String) {
        def.removeProperty(removeProperty)
    }

    fun removeVariable(removeVariable: String) {
        def.removeVariable(removeVariable)
    }

    fun resequence(resequence: Expression, i: ResequenceDsl.() -> Unit = {}) {
        val resequenceDef = def.resequence(resequence)
        ResequenceDsl(resequenceDef).apply(i)
    }

    fun resumable(i: ResumableDsl.() -> Unit) {
        val resumableDef = def.resumable()
        ResumableDsl(resumableDef).apply(i)
    }

    fun rollback() {
        def.rollback()
    }

    fun rollback(rollback: String) {
        def.rollback(rollback)
    }

    fun markRollbackOnly() {
        def.markRollbackOnly()
    }

    fun markRollbackOnlyLast() {
        def.markRollbackOnlyLast()
    }

    fun routingSlip(routingSlip: Expression, i: RoutingSlipDsl.() -> Unit = {}) {
        val routingSlipDef = def.routingSlip(routingSlip)
        RoutingSlipDsl(routingSlipDef).apply(i)
    }

    fun saga(i: SagaDsl.() -> Unit) {
        val sagaDef = def.saga()
        SagaDsl(sagaDef).apply(i)
        sagaDef.end()
    }

    fun sample(i: SampleDsl.() -> Unit = {}) {
        val sampleDef = def.sample()
        SampleDsl(sampleDef).apply(i)
    }

    fun script(script: Expression) {
        def.script(script)
    }

    fun setBody(setBody: Expression) {
        def.setBody(setBody)
    }

    fun setBody(function: (Exchange) -> Any) {
        def.setBody(function)
    }

    fun setExchangePattern(setExchangePattern: ExchangePattern) {
        def.setExchangePattern(setExchangePattern)
    }

    fun setExchangePattern(setExchangePattern: String) {
        def.setExchangePattern(setExchangePattern)
    }

    fun setHeader(setHeader: String, value: String) {
        def.setHeader(setHeader, constant(value))
    }

    fun setHeader(setHeader: String, expression: Expression) {
        def.setHeader(setHeader, expression)
    }

    fun setHeader(setHeader: String, function: () -> Any) {
        def.setHeader(setHeader, function)
    }

    fun setProperty(setProperty: String, value: String) {
        def.setProperty(setProperty, constant(value))
    }

    fun setProperty(setProperty: String, expression: Expression) {
        def.setProperty(setProperty, expression)
    }

    fun setProperty(setProperty: String, function: () -> Any) {
        def.setProperty(setProperty, function)
    }

    fun setVariable(setVariable: String, expression: Expression) {
        def.setVariable(setVariable, expression)
    }

    fun setVariable(setVariable: String, function: () -> Any) {
        def.setVariable(setVariable, function)
    }

    fun sort(sort: Expression, comparator: Comparator<*>? = null) {
        def.sort(sort, comparator)
    }

    fun split(split: Expression, i: SplitDsl.() -> Unit = {}) {
        val splitDef = def.split(split)
        SplitDsl(splitDef).apply(i)
    }

    fun step(i: StepsDsl.() -> Unit) {
        val stepDef = def.step()
        StepsDsl(stepDef).apply(i)
        stepDef.end()
    }

    fun stop() {
        def.stop()
    }

    fun threads(i: ThreadsDsl.() -> Unit) {
        val threadsDef = def.threads()
        ThreadsDsl(threadsDef).apply(i)
    }

    fun threads(poolSize: Int, i: ThreadsDsl.() -> Unit = {}) {
        val threadsDef = def.threads(poolSize)
        ThreadsDsl(threadsDef).apply(i)
    }

    fun throttle(throttle: Long, i: ThrottleDsl.() -> Unit) {
        this.throttle(constant(throttle.toString()), i)
    }

    fun throttle(throttle: Expression, i: ThrottleDsl.() -> Unit) {
        val throttleDef = def.throttle(throttle)
        ThrottleDsl(throttleDef).apply(i)
    }

    fun throwException(throwException: Exception) {
        def.throwException(throwException)
    }

    fun throwException(throwException: KClass<out Exception>, message: String) {
        def.throwException(throwException.java, message)
    }

    fun to(i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        def.to(uri)
    }

    fun toD(config: ToDynamicDsl.() -> Unit = {}, i: UriDsl.() -> Unit) {
        val toDDef = ToDynamicDefinition()
        ToDynamicDsl(toDDef).apply(config)
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        toDDef.uri = uri
        def.addOutput(toDDef)
    }

    fun transacted() {
        def.transacted()
    }

    fun transacted(ref: String) {
        def.transacted(ref)
    }

    fun transform(transform: Expression) {
        def.transform(transform)
    }

    fun transform(to: DataType) {
        def.transform(to)
    }

    fun transform(from: DataType, to: DataType) {
        def.transform(from, to)
    }

    fun doTry(i: TryDsl.() -> Unit) {
        val tryDef = def.doTry()
        TryDsl(tryDef).apply(i)
        tryDef.end()
    }

    fun unmarshal(i: DataFormatDsl.() -> Unit) {
        val dsl = DataFormatDsl().apply(i)
        def.unmarshal(dsl.def)
    }

    fun unmarshal(allowNullBody: Boolean, i: DataFormatDsl.() -> Unit) {
        val dsl = DataFormatDsl().apply(i)
        def.unmarshal(dsl.def, allowNullBody)
    }

    fun validate(validate: Predicate, i: ValidateDsl.() -> Unit = {}) {
        val validateDef = def.validate(validate)
        ValidateDsl(validateDef).apply(i)
    }

    fun wireTap(config: WireTapDsl.() -> Unit = {}, i: UriDsl.() -> Unit) {
        val dsl = UriDsl().apply(i)
        val uri = dsl.toUri()
        val wireTapDef = WireTapDefinition()
        wireTapDef.uri(uri)
        def.addOutput(wireTapDef)
        WireTapDsl(wireTapDef).apply(config)
    }
}
