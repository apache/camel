/**
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
package org.apache.camel.groovy.extend;

import java.lang.reflect.Method;

import groovy.lang.Closure;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.DataFormatClause;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.groovy.dataformat.XmlParserDataFormat;
import org.apache.camel.groovy.dataformat.XmlSlurperDataFormat;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ExpressionSupport;

/**
 * Extension class containing static methods that mainly allow to use Closures
 * instead of Predicates, Expressions, Processors, or AggregationStrategies
 */
public final class CamelGroovyMethods {

    private CamelGroovyMethods() {
        // Utility Class
    }

    // Extension Methods that use Closures to encapsulate logic

    public static ProcessorDefinition<?> process(ProcessorDefinition<?> self,
            Closure<?> processorLogic) {
        return self.process(toProcessor(processorLogic));
    }

    public WireTapDefinition<?> newExchange(WireTapDefinition<?> self,
            Closure<?> processorLogic) {
        return self.newExchange(toProcessor(processorLogic));
    }

    public static OnExceptionDefinition onRedelivery(OnExceptionDefinition self,
            Closure<Exchange> processorLogic) {
        return self.onRedelivery(toProcessor(processorLogic));
    }

    public static ProcessorDefinition<?> enrich(ProcessorDefinition<?> self, String resourceUri,
            Closure<Exchange> aggregationLogic) {
        return self.enrich(resourceUri, toAggregationStrategy(aggregationLogic));
    }

    public static ProcessorDefinition<?> pollEnrich(ProcessorDefinition<?> self,
            String resourceUri, Closure<Exchange> aggregationLogic) {
        return self.pollEnrich(resourceUri, toAggregationStrategy(aggregationLogic));
    }

    public static ProcessorDefinition<?> pollEnrich(ProcessorDefinition<?> self,
            String resourceUri, long timeout, Closure<Exchange> aggregationLogic) {
        return self.pollEnrich(resourceUri, timeout, toAggregationStrategy(aggregationLogic));
    }

    public static MulticastDefinition aggregationStrategy(MulticastDefinition self,
            Closure<Exchange> aggregationLogic) {
        return self.aggregationStrategy(toAggregationStrategy(aggregationLogic));
    }

    public static RecipientListDefinition<?> aggregationStrategy(RecipientListDefinition<?> self,
            Closure<Exchange> aggregationLogic) {
        return self.aggregationStrategy(toAggregationStrategy(aggregationLogic));
    }

    public static SplitDefinition aggregationStrategy(SplitDefinition self,
            Closure<Exchange> aggregationLogic) {
        return self.aggregationStrategy(toAggregationStrategy(aggregationLogic));
    }

    public static AggregateDefinition aggregationStrategy(AggregateDefinition self,
            Closure<Exchange> aggregationLogic) {
        return self.aggregationStrategy(toAggregationStrategy(aggregationLogic));
    }

    public static MulticastDefinition onPrepare(MulticastDefinition self,
            Closure<Exchange> processorLogic) {
        return self.onPrepare(toProcessor(processorLogic));
    }

    public static RecipientListDefinition<?> onPrepare(RecipientListDefinition<?> self,
            Closure<Exchange> processorLogic) {
        return self.onPrepare(toProcessor(processorLogic));
    }

    public static SplitDefinition onPrepare(SplitDefinition self, Closure<Exchange> processorLogic) {
        return self.onPrepare(toProcessor(processorLogic));
    }

    public static WireTapDefinition<?> onPrepare(WireTapDefinition<?> self,
            Closure<Exchange> processorLogic) {
        return self.onPrepare(toProcessor(processorLogic));
    }

    // Extension Methods that use Closures as expressions

    public static ProcessorDefinition<?> script(ProcessorDefinition<?> self,
            Closure<?> expression) {
        return self.script(toExpression(expression));
    }

    public static ProcessorDefinition<?> transform(ProcessorDefinition<?> self,
            Closure<?> expression) {
        return self.transform(toExpression(expression));
    }

    public static ProcessorDefinition<?> setProperty(ProcessorDefinition<?> self, String name,
            Closure<?> expression) {
        return self.setProperty(name, toExpression(expression));
    }

    public static ProcessorDefinition<?> setHeader(ProcessorDefinition<?> self, String name,
            Closure<?> expression) {
        return self.setHeader(name, toExpression(expression));
    }

    public static ProcessorDefinition<?> setBody(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.setBody(toExpression(expression));
    }

    public static ProcessorDefinition<?> setFaultBody(ProcessorDefinition<?> self,
            Closure<?> expression) {
        return self.setFaultBody(toExpression(expression));
    }

    public static ProcessorDefinition<?> sort(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.sort(toExpression(expression));
    }

    public static IdempotentConsumerDefinition idempotentConsumer(ProcessorDefinition<?> self,
            Closure<?> expression) {
        return self.idempotentConsumer(toExpression(expression));
    }

    public static IdempotentConsumerDefinition idempotentConsumer(ProcessorDefinition<?> self,
            IdempotentRepository<?> rep, Closure<?> expression) {
        return self.idempotentConsumer(toExpression(expression), rep);
    }

    public static RecipientListDefinition<?> recipientList(ProcessorDefinition<?> self,
            Closure<?> recipients) {
        return self.recipientList(toExpression(recipients));
    }

    public static RecipientListDefinition<?> recipientList(ProcessorDefinition<?> self,
            String delimiter, Closure<?> recipients) {
        return self.recipientList(toExpression(recipients), delimiter);
    }

    public static RoutingSlipDefinition<?> routingSlip(ProcessorDefinition<?> self,
            Closure<?> recipients) {
        return self.routingSlip(toExpression(recipients));
    }

    public static RoutingSlipDefinition<?> routingSlip(ProcessorDefinition<?> self,
            String delimiter, Closure<?> recipients) {
        return self.routingSlip(toExpression(recipients), delimiter);
    }

    public static DynamicRouterDefinition<?> dynamicRouter(ProcessorDefinition<?> self,
            Closure<?> expression) {
        return self.dynamicRouter(toExpression(expression));
    }

    public static SplitDefinition split(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.split(toExpression(expression));
    }

    public static ResequenceDefinition resequence(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.resequence(toExpression(expression));
    }

    public static AggregateDefinition aggregate(ProcessorDefinition<?> self,
            Closure<?> correlationExpression) {
        return self.aggregate(toExpression(correlationExpression));
    }

    public static AggregateDefinition completionSize(AggregateDefinition self, Closure<?> expression) {
        return self.completionSize(toExpression(expression));
    }

    public static AggregateDefinition completionTimeout(AggregateDefinition self,
            Closure<?> expression) {
        return self.completionTimeout(toExpression(expression));
    }

    public static DelayDefinition delay(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.delay(toExpression(expression));
    }

    public static ThrottleDefinition throttle(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.throttle(toExpression(expression));
    }

    public static LoopDefinition loop(ProcessorDefinition<?> self, Closure<?> expression) {
        return self.loop(toExpression(expression));
    }

    public static WireTapDefinition<?> newExchangeBody(WireTapDefinition<?> self,
            Closure<?> expression) {
        return self.newExchangeBody(toExpression(expression));
    }

    public static WireTapDefinition<?> newExchangeHeader(WireTapDefinition<?> self, String header,
            Closure<?> expression) {
        return self.newExchangeHeader(header, toExpression(expression));
    }

    // Extension Methods that use Closures as predicates

    public static FilterDefinition filter(ProcessorDefinition<?> self, Closure<?> predicate) {
        return self.filter(toExpression(predicate));
    }

    public static ProcessorDefinition<?> validate(ProcessorDefinition<?> self, Closure<?> predicate) {
        return self.validate((Predicate) toExpression(predicate));
    }

    public static ChoiceDefinition when(ChoiceDefinition self, Closure<?> predicate) {
        return self.when(toExpression(predicate));
    }

    public static TryDefinition onWhen(TryDefinition self, Closure<?> predicate) {
        return self.onWhen(toExpression(predicate));
    }

    public static OnExceptionDefinition onWhen(OnExceptionDefinition self, Closure<?> predicate) {
        return self.onWhen(toExpression(predicate));
    }

    public static OnExceptionDefinition handled(OnExceptionDefinition self, Closure<?> predicate) {
        return self.handled((Predicate) toExpression(predicate));
    }

    public static OnExceptionDefinition continued(OnExceptionDefinition self, Closure<?> predicate) {
        return self.continued((Predicate) toExpression(predicate));
    }

    public static OnExceptionDefinition retryWhile(OnExceptionDefinition self, Closure<?> predicate) {
        return self.retryWhile(toExpression(predicate));
    }

    public static OnCompletionDefinition onWhen(OnCompletionDefinition self, Closure<?> predicate) {
        return self.onWhen(toExpression(predicate));
    }

    public static CatchDefinition onWhen(CatchDefinition self, Closure<?> predicate) {
        return self.onWhen(toExpression(predicate));
    }

    public static AggregateDefinition completionPredicate(AggregateDefinition self,
            Closure<?> predicate) {
        return self.completionPredicate(toExpression(predicate));
    }

    public static InterceptDefinition when(InterceptDefinition self, Closure<?> predicate) {
        return self.when(toExpression(predicate));
    }

    public static InterceptSendToEndpointDefinition when(InterceptSendToEndpointDefinition self,
            Closure<?> predicate) {
        return self.when(toExpression(predicate));
    }

    // Bridging generic attribution of expressions, predicates etc.

    public static AggregationStrategy aggregator(RouteBuilder self,
            Closure<Exchange> aggregationLogic) {
        return toAggregationStrategy(aggregationLogic);
    }

    public static Expression expression(RouteBuilder self, Closure<?> expression) {
        return toExpression(expression);
    }

    public static Predicate predicate(RouteBuilder self, Closure<?> predicate) {
        return toExpression(predicate);
    }

    public static Processor processor(RouteBuilder self, Closure<Exchange> processor) {
        return toProcessor(processor);
    }

    public static <T> T expression(ExpressionClause<T> self, Closure<?> expression) {
        return self.expression(toExpression(expression));
    }

    // Private Helpers

    static ExpressionSupport toExpression(final Closure<?> closure) {
        return new ClosureExpression(closure);
    }

    static Processor toProcessor(final Closure<?> closure) {
        return new ClosureProcessor(closure);
    }

    static AggregationStrategy toAggregationStrategy(final Closure<Exchange> closure) {
        return new ClosureAggregationStrategy(closure);
    }

    // Groovy-specific data formats

    public static ProcessorDefinition<?> gnode(DataFormatClause<?> self, boolean namespaceAware) {
        return dataFormat(self, parser(namespaceAware));
    }

    public static ProcessorDefinition<?> gnode(DataFormatClause<?> self) {
        return gnode(self, true);
    }

    public static ProcessorDefinition<?> gpath(DataFormatClause<?> self, boolean namespaceAware) {
        return dataFormat(self, slurper(namespaceAware));
    }

    public static ProcessorDefinition<?> gpath(DataFormatClause<?> self) {
        return gpath(self, true);
    }

    private static DataFormatDefinition slurper(boolean namespaceAware) {
        return new DataFormatDefinition(new XmlSlurperDataFormat(namespaceAware));
    }

    private static DataFormatDefinition parser(boolean namespaceAware) {
        return new DataFormatDefinition(new XmlParserDataFormat(namespaceAware));
    }

    // DataFormatClause.dataFormat(DataFormatDefinition) is private...
    private static ProcessorDefinition<?> dataFormat(DataFormatClause<?> self,
            DataFormatDefinition format) {
        try {
            Method m = self.getClass().getDeclaredMethod("dataFormat", DataFormatDefinition.class);
            m.setAccessible(true);
            return (ProcessorDefinition<?>) m.invoke(self, format);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown DataFormat operation", e);
        }
    }

}
