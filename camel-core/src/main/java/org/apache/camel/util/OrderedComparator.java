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

import org.apache.camel.Ordered;

/**
 * A comparator to sort {@link Ordered}
 *
 * @version 
 */
public final class OrderedComparator implements Comparator<Object> {

    private static final OrderedComparator INSTANCE = new OrderedComparator();
    private static final OrderedComparator INSTANCE_REVERSE = new OrderedComparator(true);

    private final boolean reverse;

    /**
     * Favor using the static instance {@link #get()}
     */
    public OrderedComparator() {
        this(false);
    }

    /**
     * Favor using the static instance {@link #getReverse()}
     */
    public OrderedComparator(boolean reverse) {
        this.reverse = reverse;
    }

    /**
     * Gets the comparator that sorts a..z
     */
    public static OrderedComparator get() {
        return INSTANCE;
    }

    /**
     * Gets the comparator that sorts z..a (reverse)
     */
    public static OrderedComparator getReverse() {
        return INSTANCE_REVERSE;
    }

    public int compare(Object o1, Object o2) {
        Integer num1 = 0;
        Integer num2 = 0;
        if (o1 instanceof Ordered) {
            num1 = ((Ordered) o1).getOrder();
        }
        if (o2 instanceof Ordered) {
            num2 = ((Ordered) o2).getOrder();
        }
        int answer = num1.compareTo(num2);
        return reverse ? -1 * answer : answer;
    }
}
