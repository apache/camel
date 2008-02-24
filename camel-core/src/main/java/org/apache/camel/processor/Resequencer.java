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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.util.ExpressionComparator;
import org.apache.camel.util.ExpressionListComparator;

/**
 * An implementation of the <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a>
 * which can reorder messages within a batch.
 *
 * @version $Revision$
 */
public class Resequencer extends BatchProcessor {
    public Resequencer(Endpoint endpoint, Processor processor, Expression<Exchange> expression) {
        this(endpoint, processor, createSet(expression));
    }

    public Resequencer(Endpoint endpoint, Processor processor, List<Expression> expressions) {
        this(endpoint, processor, createSet(expressions));
    }

    public Resequencer(Endpoint endpoint, Processor processor, Set<Exchange> collection) {
        super(endpoint, processor, collection);
    }

    @Override
    public String toString() {
        return "Resequencer[to: " + getProcessor() + "]";
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected static Set<Exchange> createSet(Expression<Exchange> expression) {
        return createSet(new ExpressionComparator<Exchange>(expression));
    }

    protected static Set<Exchange> createSet(List<Expression> expressions) {
        if (expressions.size() == 1) {
            return createSet(expressions.get(0));
        }
        return createSet(new ExpressionListComparator(expressions));
    }

    protected static Set<Exchange> createSet(Comparator<? super Exchange> comparator) {
        return new TreeSet<Exchange>(comparator);
    }
}
