/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jsonpath.easypredicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EasyPredicateOperators {

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

    private static final String[] OPS = new String[]{EQ, NE, LT, LE, GT, GE, REG, IN, NIN, SIZE, EMPTY};

    private static final Pattern PATTERN = Pattern.compile("\\s(" + Arrays.stream(OPS).collect(Collectors.joining("|")) + ")\\s");

    /**
     * Does the expression have any operator?
     */
    public static boolean hasOperator(String exp) {
        return Arrays.stream(OPS).anyMatch(o -> exp.contains(" " + o + ""));
    }

    /**
     * Is this an operator
     */
    static boolean isOperator(String exp) {
        return Arrays.stream(OPS).anyMatch(s -> Objects.equals(s, exp));
    }

    /**
     * Gets the operator
     */
    static String getOperatorAtStart(String exp) {
        return Arrays.stream(OPS).filter(o -> exp.startsWith(" " + o + "")).findFirst().orElse(null);
    }

    public static String[] tokens(String exp) {
        List<String> list = new ArrayList<>();

        StringBuilder part = new StringBuilder();
        for (int i = 0; i < exp.length(); i++) {

            // is there a new operator
            String s = exp.substring(i);
            String op = getOperatorAtStart(s);
            if (op != null) {
                if (part.length() > 0) {
                    list.add(part.toString());
                    part.setLength(0);
                }
                list.add(op.trim());
                // move i ahead
                i = i + op.length() + 1;
            } else {
                char ch = exp.charAt(i);
                part.append(ch);
            }
        }

        // ant leftovers
        if (part.length() > 0) {
            list.add(part.toString());
        }

        return list.toArray(new String[list.size()]);
    }

}
