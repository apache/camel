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
import org.apache.camel.Predicate
import org.apache.camel.builder.PredicateBuilder
import java.util.regex.Pattern
import kotlin.reflect.KClass

fun Expression.toPredicate(): Predicate {
    return PredicateBuilder.toPredicate(this)
}

fun Predicate.not(): Predicate {
    return PredicateBuilder.not(this)
}

infix fun Predicate.and(and: Predicate): Predicate {
    return PredicateBuilder.and(this, and)
}

infix fun Predicate.or(or: Predicate): Predicate {
    return PredicateBuilder.or(this, or)
}

fun and(vararg and: Predicate): Predicate {
    return PredicateBuilder.and(*and)
}

fun or(vararg or: Predicate): Predicate {
    return PredicateBuilder.or(*or)
}

fun `in`(vararg `in`: Predicate): Predicate {
    return PredicateBuilder.`in`(*`in`)
}

infix fun Expression.isEqualTo(isEqualTo: Expression): Predicate {
    return PredicateBuilder.isEqualTo(this, isEqualTo)
}

infix fun Expression.isEqualToIgnoreCase(isEqualToIgnoreCase: Expression): Predicate {
    return PredicateBuilder.isEqualToIgnoreCase(this, isEqualToIgnoreCase)
}

infix fun Expression.isNotEqualTo(isNotEqualTo: Expression): Predicate {
    return PredicateBuilder.isNotEqualTo(this, isNotEqualTo)
}

infix fun Expression.isLessThan(isLessThan: Expression): Predicate {
    return PredicateBuilder.isLessThan(this, isLessThan)
}

infix fun Expression.isLessThanOrEqualTo(isLessThanOrEqualTo: Expression): Predicate {
    return PredicateBuilder.isLessThanOrEqualTo(this, isLessThanOrEqualTo)
}

infix fun Expression.isGreaterThan(isGreaterThan: Expression): Predicate {
    return PredicateBuilder.isGreaterThan(this, isGreaterThan)
}

infix fun Expression.isGreaterThanOrEqualTo(isGreaterThanOrEqualTo: Expression): Predicate {
    return PredicateBuilder.isGreaterThanOrEqualTo(this, isGreaterThanOrEqualTo)
}

infix fun Expression.contains(contains: Expression): Predicate {
    return PredicateBuilder.contains(this, contains)
}

infix fun Expression.containsIgnoreCase(containsIgnoreCase: Expression): Predicate {
    return PredicateBuilder.containsIgnoreCase(this, containsIgnoreCase)
}

fun Expression.isNull(): Predicate {
    return PredicateBuilder.isNull(this)
}

fun Expression.isNotNull(): Predicate {
    return PredicateBuilder.isNotNull(this)
}

infix fun Expression.isInstanceOf(isInstanceOf: KClass<*>): Predicate {
    return PredicateBuilder.isInstanceOf(this, isInstanceOf.java)
}

infix fun Expression.startsWith(startsWith: Expression): Predicate {
    return PredicateBuilder.startsWith(this, startsWith)
}

infix fun Expression.endsWith(endsWith: Expression): Predicate {
    return PredicateBuilder.endsWith(this, endsWith)
}

infix fun Expression.regex(regex: String): Predicate {
    return PredicateBuilder.regex(this, regex)
}

infix fun Expression.regex(regex: Pattern): Predicate {
    return PredicateBuilder.regex(this, regex)
}