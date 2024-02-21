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
import org.apache.camel.Message
import org.apache.camel.builder.ExpressionClause

import java.util.function.Function

@CompileStatic
class ExpressionClauseExtensions {
    static <T> T body(ExpressionClause<T> self, Closure<?> callable) {
        return self.body(new Function<Object, Object>() {
            @Override
            Object apply(Object body) {
                callable.resolveStrategy = Closure.DELEGATE_ONLY
                return callable.call(body)
            }
        })
    }

    static <T, B> T body(ExpressionClause<T> self, Class<B> type, Closure<?> callable) {
        return self.body(type, new Function<B, Object>() {
            @Override
            Object apply(B body) {
                callable.resolveStrategy = Closure.DELEGATE_ONLY
                return callable.call(body)
            }
        })
    }

    static <T> T message(ExpressionClause<T> self, @DelegatesTo(Message) Closure<?> callable) {
        return self.message(new Function<Message, Object>() {
            @Override
            Object apply(Message body) {
                callable.resolveStrategy = Closure.DELEGATE_ONLY
                return callable.call(body)
            }
        })
    }
}
