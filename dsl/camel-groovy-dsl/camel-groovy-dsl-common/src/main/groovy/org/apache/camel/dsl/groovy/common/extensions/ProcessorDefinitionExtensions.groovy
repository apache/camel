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
package org.apache.camel.dsl.groovy.common.extensions

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.apache.camel.Processor
import org.apache.camel.model.ProcessorDefinition

import java.util.function.Function

@CompileStatic
class ProcessorDefinitionExtensions {
    static <T extends ProcessorDefinition<T>> T setBody(ProcessorDefinition<T> self, Closure<?> callable) {
        return self.setBody(new Function<Exchange, Object>() {
            @Override
            Object apply(Exchange exchange) {
                callable.resolveStrategy = Closure.DELEGATE_ONLY
                return callable.call(exchange)
            }
        });
    }

    static <T extends ProcessorDefinition<T>> T setHeader(ProcessorDefinition<T> self, String name, Closure<?> callable) {
        return self.setHeader(name, new Expression() {
            @Override
            def <T> T evaluate(Exchange exchange, Class<T> type) {
                callable.resolveStrategy = Closure.DELEGATE_ONLY
                Object obj = callable.call(exchange)
                return exchange.getContext().getTypeConverter().convertTo(type, exchange, obj)
            }
        });
    }

    static <T extends ProcessorDefinition<T>> T process(ProcessorDefinition<T> self, Closure<?> callable) {
        return self.process(new Processor() {
            @Override
            void process(Exchange exchange) throws Exception {
                callable.resolveStrategy = Closure.DELEGATE_ONLY
                callable.call(exchange)
            }
        });
    }
}
