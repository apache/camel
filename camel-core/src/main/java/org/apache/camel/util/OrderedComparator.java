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

    private final boolean reverse;

    public OrderedComparator() {
        this(false);
    }

    public OrderedComparator(boolean reverse) {
        this.reverse = reverse;
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
