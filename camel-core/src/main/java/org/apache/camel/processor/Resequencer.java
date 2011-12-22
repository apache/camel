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
package org.apache.camel.processor;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.util.ExpressionComparator;

/**
 * An implementation of the <a href="http://camel.apache.org/resequencer.html">Resequencer</a>
 * which can reorder messages within a batch.
 *
 * @version 
 */
@SuppressWarnings("deprecation")
public class Resequencer extends BatchProcessor implements Traceable {

    // TODO: Rework to avoid using BatchProcessor

    public Resequencer(CamelContext camelContext, Processor processor, Expression expression) {
        this(camelContext, processor, createSet(expression, false, false), expression);
    }

    public Resequencer(CamelContext camelContext, Processor processor, Expression expression,
                       boolean allowDuplicates, boolean reverse) {
        this(camelContext, processor, createSet(expression, allowDuplicates, reverse), expression);
    }

    public Resequencer(CamelContext camelContext, Processor processor, Set<Exchange> collection, Expression expression) {
        super(camelContext, processor, collection, expression);
    }

    @Override
    public String toString() {
        return "Resequencer[to: " + getProcessor() + "]";
    }

    public String getTraceLabel() {
        return "resequencer";
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected static Set<Exchange> createSet(Expression expression, boolean allowDuplicates, boolean reverse) {
        return createSet(new ExpressionComparator(expression), allowDuplicates, reverse);
    }

    protected static Set<Exchange> createSet(final Comparator<? super Exchange> comparator, boolean allowDuplicates, boolean reverse) {
        Comparator<? super Exchange> answer = comparator;

        if (reverse) {
            answer = new Comparator<Exchange>() {
                public int compare(Exchange o1, Exchange o2) {
                    int answer = comparator.compare(o1, o2);
                    // reverse it
                    return answer * -1;
                }
            };
        }

        // if we allow duplicates then we need to cater for that in the comparator
        final Comparator<? super Exchange> forAllowDuplicates = answer;
        if (allowDuplicates) {
            answer = new Comparator<Exchange>() {
                public int compare(Exchange o1, Exchange o2) {
                    int answer = forAllowDuplicates.compare(o1, o2);
                    if (answer == 0) {
                        // they are equal but we should allow duplicates so say that o2 is higher
                        // so it will come next
                        return 1;
                    }
                    return answer;
                }
            };
        }

        return new TreeSet<Exchange>(answer);
    }

}
