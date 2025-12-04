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

package org.apache.camel.component.exec.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.camel.util.StringQuoteHelper;

/**
 * Utility class for parsing, used by the Camel Exec component.<br>
 * Note: the class should be dropped, when the commons-exec library implements similar functionality.
 */
public final class ExecParseUtils {

    private ExecParseUtils() {}

    public static List<String> splitToWhiteSpaceSeparatedTokens(String input) {
        List<String> answer = new ArrayList<>();
        if (input == null) {
            return answer;
        }

        String[] arr = StringQuoteHelper.splitSafeQuote(input, ' ', true, false);
        Collections.addAll(answer, arr);
        return answer;
    }

    public static List<Integer> splitCommaSeparatedToListOfInts(String commaSeparatedInts) {
        List<Integer> exitValues = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(commaSeparatedInts, ",");
        while (st.hasMoreTokens()) {
            exitValues.add(Integer.valueOf(st.nextToken()));
        }
        return exitValues;
    }
}
