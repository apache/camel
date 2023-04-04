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
package org.apache.camel.jsonpath.easypredicate;

import java.util.Arrays;
import java.util.Objects;

/**
 * Json path operators
 */
public final class EasyPredicateOperators {

    private static final String EQ = "==";
    private static final String NE = "!=";
    private static final String LT = "<";
    private static final String LE = "<=";
    private static final String GT = ">";
    private static final String GE = ">=";
    private static final String REG = "=~";
    private static final String IN = "in";
    private static final String NIN = "nin";
    private static final String SIZE = "size";
    private static final String EMPTY = "empty";

    private static final String[] OPS = new String[] { EQ, NE, LT, LE, GT, GE, REG, IN, NIN, SIZE, EMPTY };

    private EasyPredicateOperators() {
    }

    /**
     * Does the expression have any operator (with single space around)?
     */
    static boolean hasOperator(String exp) {
        // need to have space around operator to not match eg in used in some other word
        return Arrays.stream(OPS).anyMatch(o -> exp.contains(" " + o));
    }

    /**
     * Is this an operator (with no space around)
     */
    static boolean isOperator(String exp) {
        return Arrays.stream(OPS).anyMatch(s -> Objects.equals(s, exp));
    }

    /**
     * Gets the operator (with single space around)
     */
    static String getOperatorAtStart(String exp) {
        return Arrays.stream(OPS).filter(o -> exp.startsWith(" " + o)).findFirst().orElse(null);
    }

}
