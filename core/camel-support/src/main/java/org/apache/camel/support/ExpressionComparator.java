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
package org.apache.camel.support;

import java.util.Comparator;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * An implementation of {@link Comparator} that takes an {@link Expression} which is evaluated
 * on each exchange to be compared
 */
public class ExpressionComparator implements Comparator<Exchange> {
    private final Expression expression;

    public ExpressionComparator(Expression expression) {
        this.expression = expression;
    }

    @Override
    public int compare(Exchange e1, Exchange e2) {
        Object o1 = expression.evaluate(e1, Object.class);
        Object o2 = expression.evaluate(e2, Object.class);

        // if they are numeric then use numeric comparison instead of text
        Long l1 = e1.getContext().getTypeConverter().tryConvertTo(Long.class, e1, o1);
        Long l2 = e1.getContext().getTypeConverter().tryConvertTo(Long.class, e2, o2);
        if (l1 != null && l2 != null) {
            return l1.compareTo(l2);
        }

        return ObjectHelper.compare(o1, o2);
    }
}
