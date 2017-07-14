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

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.Ordered;

/**
 * @version 
 */
public class OrderedComparatorTest extends TestCase {

    public void testOrderedComparatorGet() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(2));
        answer.add(new MyOrder(1));
        answer.add(new MyOrder(5));
        answer.add(new MyOrder(4));

        answer.sort(OrderedComparator.get());

        assertEquals(0, answer.get(0).getOrder());
        assertEquals(1, answer.get(1).getOrder());
        assertEquals(2, answer.get(2).getOrder());
        assertEquals(4, answer.get(3).getOrder());
        assertEquals(5, answer.get(4).getOrder());
    }

    public void testOrderedComparator() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(2));
        answer.add(new MyOrder(1));
        answer.add(new MyOrder(5));
        answer.add(new MyOrder(4));

        answer.sort(new OrderedComparator());

        assertEquals(0, answer.get(0).getOrder());
        assertEquals(1, answer.get(1).getOrder());
        assertEquals(2, answer.get(2).getOrder());
        assertEquals(4, answer.get(3).getOrder());
        assertEquals(5, answer.get(4).getOrder());
    }

    public void testOrderedComparatorGetReverse() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(2));
        answer.add(new MyOrder(1));
        answer.add(new MyOrder(5));
        answer.add(new MyOrder(4));

        answer.sort(OrderedComparator.getReverse());

        assertEquals(5, answer.get(0).getOrder());
        assertEquals(4, answer.get(1).getOrder());
        assertEquals(2, answer.get(2).getOrder());
        assertEquals(1, answer.get(3).getOrder());
        assertEquals(0, answer.get(4).getOrder());
    }

    public void testOrderedComparatorReverse() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(2));
        answer.add(new MyOrder(1));
        answer.add(new MyOrder(5));
        answer.add(new MyOrder(4));

        answer.sort(new OrderedComparator(true));

        assertEquals(5, answer.get(0).getOrder());
        assertEquals(4, answer.get(1).getOrder());
        assertEquals(2, answer.get(2).getOrder());
        assertEquals(1, answer.get(3).getOrder());
        assertEquals(0, answer.get(4).getOrder());
    }

    public void testOrderedComparatorHigh() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(2));
        answer.add(new MyOrder(200));
        answer.add(new MyOrder(50));
        answer.add(new MyOrder(Ordered.HIGHEST));
        answer.add(new MyOrder(4));

        answer.sort(new OrderedComparator());

        assertEquals(Ordered.HIGHEST, answer.get(0).getOrder());
        assertEquals(0, answer.get(1).getOrder());
        assertEquals(2, answer.get(2).getOrder());
        assertEquals(4, answer.get(3).getOrder());
        assertEquals(50, answer.get(4).getOrder());
        assertEquals(200, answer.get(5).getOrder());
    }

    public void testOrderedComparatorHighReverse() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(2));
        answer.add(new MyOrder(200));
        answer.add(new MyOrder(50));
        answer.add(new MyOrder(Ordered.HIGHEST));
        answer.add(new MyOrder(4));

        answer.sort(new OrderedComparator(true));

        assertEquals(200, answer.get(0).getOrder());
        assertEquals(50, answer.get(1).getOrder());
        assertEquals(4, answer.get(2).getOrder());
        assertEquals(2, answer.get(3).getOrder());
        assertEquals(0, answer.get(4).getOrder());
        assertEquals(Ordered.HIGHEST, answer.get(5).getOrder());
    }

    public void testOrderedComparatorLow() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(-2));
        answer.add(new MyOrder(200));
        answer.add(new MyOrder(50));
        answer.add(new MyOrder(Ordered.LOWEST));
        answer.add(new MyOrder(-4));

        answer.sort(new OrderedComparator());

        assertEquals(-4, answer.get(0).getOrder());
        assertEquals(-2, answer.get(1).getOrder());
        assertEquals(0, answer.get(2).getOrder());
        assertEquals(50, answer.get(3).getOrder());
        assertEquals(200, answer.get(4).getOrder());
        assertEquals(Ordered.LOWEST, answer.get(5).getOrder());
    }

    public void testOrderedComparatorLowReverse() throws Exception {
        List<Ordered> answer = new ArrayList<Ordered>();
        answer.add(new MyOrder(0));
        answer.add(new MyOrder(-2));
        answer.add(new MyOrder(200));
        answer.add(new MyOrder(50));
        answer.add(new MyOrder(Ordered.LOWEST));
        answer.add(new MyOrder(-4));

        answer.sort(new OrderedComparator(true));

        assertEquals(Ordered.LOWEST, answer.get(0).getOrder());
        assertEquals(200, answer.get(1).getOrder());
        assertEquals(50, answer.get(2).getOrder());
        assertEquals(0, answer.get(3).getOrder());
        assertEquals(-2, answer.get(4).getOrder());
        assertEquals(-4, answer.get(5).getOrder());
    }

    private static final class MyOrder implements Ordered {

        private final int order;

        private MyOrder(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }

        @Override
        public String toString() {
            return "" + order;
        }
    }
}
