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
package org.apache.camel.util;

import java.util.Comparator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * An implementation of {@link java.util.Comparator} which takes a list of
 * {@link org.apache.camel.Expression} objects which is evaluated
 * on each exchange to compare them
 *
 * @version 
 */
public class ExpressionListComparator implements Comparator<Exchange> {
    private final List<Expression> expressions;

    public ExpressionListComparator(List<Expression> expressions) {
        this.expressions = expressions;
    }

    public int compare(Exchange e1, Exchange e2) {
        for (Expression expression : expressions) {
            Object o1 = expression.evaluate(e1, Object.class);
            Object o2 = expression.evaluate(e2, Object.class);
            int answer = ObjectHelper.compare(o1, o2);
            if (answer != 0) {
                return answer;
            }
        }
        return 0;
    }
}