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

@Metadata(label = "operator")
public final class SimpleOperatorConstants {

    // --- Binary comparison operators (precedence 10) ---

    @Metadata(description = "Tests equality between left and right operand values. Camel will coerce the right operand type to match the left.",
              label = "binary",
              examples = { "${header.foo} == 'bar'", "${header.count} == 5" },
              annotations = { "kind=binary", "syntax=LHS == RHS", "precedence=10" })
    public static final String EQ = "==";

    @Metadata(description = "Tests equality between left and right operand values, ignoring case for string comparison.",
              label = "binary",
              examples = { "${header.foo} =~ 'BAR'" },
              annotations = { "kind=binary", "syntax=LHS =~ RHS", "precedence=10" })
    public static final String EQ_IGNORE = "=~";

    @Metadata(description = "Tests whether the left operand is greater than the right operand.",
              label = "binary",
              examples = { "${header.count} > 100" },
              annotations = { "kind=binary", "syntax=LHS > RHS", "precedence=10" })
    public static final String GT = ">";

    @Metadata(description = "Tests whether the left operand is greater than or equal to the right operand.",
              label = "binary",
              examples = { "${header.count} >= 100" },
              annotations = { "kind=binary", "syntax=LHS >= RHS", "precedence=10" })
    public static final String GTE = ">=";

    @Metadata(description = "Tests whether the left operand is less than the right operand.",
              label = "binary",
              examples = { "${header.count} < 100" },
              annotations = { "kind=binary", "syntax=LHS < RHS", "precedence=10" })
    public static final String LT = "<";

    @Metadata(description = "Tests whether the left operand is less than or equal to the right operand.",
              label = "binary",
              examples = { "${header.count} <= 100" },
              annotations = { "kind=binary", "syntax=LHS <= RHS", "precedence=10" })
    public static final String LTE = "<=";

    @Metadata(description = "Tests inequality between left and right operand values.",
              label = "binary",
              examples = { "${header.foo} != 'bar'" },
              annotations = { "kind=binary", "syntax=LHS != RHS", "precedence=10" })
    public static final String NOT_EQ = "!=";

    @Metadata(description = "Tests inequality between left and right operand values, ignoring case for string comparison.",
              label = "binary",
              examples = { "${header.foo} !=~ 'BAR'" },
              annotations = { "kind=binary", "syntax=LHS !=~ RHS", "precedence=10" })
    public static final String NOT_EQ_IGNORE = "!=~";

    @Metadata(description = "Tests whether the left operand string contains the right operand string.",
              label = "binary",
              examples = { "${header.title} contains 'Camel'" },
              annotations = { "kind=binary", "syntax=LHS contains RHS", "precedence=10" })
    public static final String CONTAINS = "contains";

    @Metadata(description = "Tests whether the left operand string does not contain the right operand string.",
              label = "binary",
              examples = { "${header.title} !contains 'Camel'" },
              annotations = { "kind=binary", "syntax=LHS !contains RHS", "precedence=10" })
    public static final String NOT_CONTAINS = "!contains";

    @Metadata(description = "Tests whether the left operand string contains the right operand string, ignoring case.",
              label = "binary",
              examples = { "${header.title} ~~ 'camel'" },
              annotations = { "kind=binary", "syntax=LHS ~~ RHS", "precedence=10" })
    public static final String CONTAINS_IGNORECASE = "~~";

    @Metadata(description = "Tests whether the left operand string does not contain the right operand string, ignoring case.",
              label = "binary",
              examples = { "${header.title} !~~ 'camel'" },
              annotations = { "kind=binary", "syntax=LHS !~~ RHS", "precedence=10" })
    public static final String NOT_CONTAINS_IGNORECASE = "!~~";

    @Metadata(description = "Tests whether the left operand matches the right operand as a regular expression.",
              label = "binary",
              examples = { "${header.number} regex '\\d{4}'" },
              annotations = { "kind=binary", "syntax=LHS regex 'pattern'", "precedence=10" })
    public static final String REGEX = "regex";

    @Metadata(description = "Tests whether the left operand does not match the right operand as a regular expression.",
              label = "binary",
              examples = { "${header.number} !regex '\\d{4}'" },
              annotations = { "kind=binary", "syntax=LHS !regex 'pattern'", "precedence=10" })
    public static final String NOT_REGEX = "!regex";

    @Metadata(description = "Tests whether the left operand is in a set of comma-separated values.",
              label = "binary",
              examples = { "${header.type} in 'gold,silver'" },
              annotations = { "kind=binary", "syntax=LHS in 'val1,val2,...'", "precedence=10" })
    public static final String IN = "in";

    @Metadata(description = "Tests whether the left operand is not in a set of comma-separated values.",
              label = "binary",
              examples = { "${header.type} !in 'gold,silver'" },
              annotations = { "kind=binary", "syntax=LHS !in 'val1,val2,...'", "precedence=10" })
    public static final String NOT_IN = "!in";

    @Metadata(description = "Tests whether the left operand is an instance of the right operand type (Java classname or short name).",
              label = "binary",
              examples = { "${header.type} is 'String'", "${body} is 'java.util.List'" },
              annotations = { "kind=binary", "syntax=LHS is 'typeName'", "precedence=10" })
    public static final String IS = "is";

    @Metadata(description = "Tests whether the left operand is not an instance of the right operand type.",
              label = "binary",
              examples = { "${header.type} !is 'String'" },
              annotations = { "kind=binary", "syntax=LHS !is 'typeName'", "precedence=10" })
    public static final String NOT_IS = "!is";

    @Metadata(description = "Tests whether the left operand is within the numeric range specified by 'from..to'.",
              label = "binary",
              examples = { "${header.number} range '100..199'" },
              annotations = { "kind=binary", "syntax=LHS range 'from..to'", "precedence=10" })
    public static final String RANGE = "range";

    @Metadata(description = "Tests whether the left operand is not within the numeric range specified by 'from..to'.",
              label = "binary",
              examples = { "${header.number} !range '100..199'" },
              annotations = { "kind=binary", "syntax=LHS !range 'from..to'", "precedence=10" })
    public static final String NOT_RANGE = "!range";

    @Metadata(description = "Tests whether the left operand string starts with the right operand string.",
              label = "binary",
              examples = { "${header.name} startsWith 'Camel'" },
              annotations = { "kind=binary", "syntax=LHS startsWith RHS", "precedence=10" })
    public static final String STARTS_WITH = "startsWith";

    @Metadata(description = "Tests whether the left operand string does not start with the right operand string.",
              label = "binary",
              examples = { "${header.name} !startsWith 'Camel'" },
              annotations = { "kind=binary", "syntax=LHS !startsWith RHS", "precedence=10" })
    public static final String NOT_STARTS_WITH = "!startsWith";

    @Metadata(description = "Tests whether the left operand string ends with the right operand string.",
              label = "binary",
              examples = { "${header.name} endsWith '.xml'" },
              annotations = { "kind=binary", "syntax=LHS endsWith RHS", "precedence=10" })
    public static final String ENDS_WITH = "endsWith";

    @Metadata(description = "Tests whether the left operand string does not end with the right operand string.",
              label = "binary",
              examples = { "${header.name} !endsWith '.xml'" },
              annotations = { "kind=binary", "syntax=LHS !endsWith RHS", "precedence=10" })
    public static final String NOT_ENDS_WITH = "!endsWith";

    // --- Unary operators (precedence 1) ---

    @Metadata(description = "Increments the numeric value by one. Must immediately follow a function closing brace.",
              label = "unary",
              examples = { "${header.count}++" },
              annotations = { "kind=unary", "syntax=${fn}++", "precedence=1" })
    public static final String INC = "++";

    @Metadata(description = "Decrements the numeric value by one. Must immediately follow a function closing brace.",
              label = "unary",
              examples = { "${header.count}--" },
              annotations = { "kind=unary", "syntax=${fn}--", "precedence=1" })
    public static final String DEC = "--";

    // --- Logical operators (precedence 30) ---

    @Metadata(description = "Logical AND. Both left and right predicates must evaluate to true.",
              label = "logical",
              examples = { "${header.title} contains 'Camel' && ${header.type} == 'gold'" },
              annotations = { "kind=logical", "syntax=predicate && predicate", "precedence=30" })
    public static final String AND = "&&";

    @Metadata(description = "Logical OR. At least one of the left or right predicates must evaluate to true.",
              label = "logical",
              examples = { "${header.title} contains 'Camel' || ${header.type} == 'gold'" },
              annotations = { "kind=logical", "syntax=predicate || predicate", "precedence=30" })
    public static final String OR = "||";

    // --- Ternary operator (precedence 25) ---

    @Metadata(description = "Ternary conditional operator. Evaluates the predicate and returns trueValue if true, falseValue if false. Requires spaces around both ? and : tokens.",
              label = "ternary",
              examples = {
                      "${header.foo} > 0 ? 'positive' : 'negative'",
                      "${header.score} >= 90 ? 'A' : ${header.score} >= 80 ? 'B' : 'C'" },
              annotations = { "kind=ternary", "syntax=predicate ? trueValue : falseValue", "precedence=25" })
    public static final String TERNARY = "? :";

    // --- Chain operators (precedence 5) ---

    @Metadata(description = "Pipes the result of the left expression as input body to the right expression. Use $param in the right expression to reference the piped value explicitly.",
              label = "chain",
              examples = { "${trim()} ~> ${uppercase()}", "${substringAfter('Hello')} ~> ${trim()} ~> ${uppercase()}" },
              annotations = { "kind=chain", "syntax=expr ~> expr", "precedence=5" })
    public static final String CHAIN = "~>";

    @Metadata(description = "Null-safe chain operator. Same as ~> but stops chaining and returns null if the left expression evaluates to null.",
              label = "chain",
              examples = { "${header.name} ?~> ${trim()} ?~> ${uppercase()}" },
              annotations = { "kind=chain", "syntax=expr ?~> expr", "precedence=5" })
    public static final String CHAIN_NULL_SAFE = "?~>";

    // --- Other operators (precedence 20) ---

    @Metadata(description = "Elvis operator (null-coalescing). Returns the left operand if it is not null/empty, otherwise returns the right operand as a fallback value.",
              label = "other",
              examples = { "${header.username} ?: 'Guest'", "${body} ?: ${header.default}" },
              annotations = { "kind=other", "syntax=expr ?: defaultValue", "precedence=20" })
    public static final String ELVIS = "?:";

    private SimpleOperatorConstants() {
    }
}
