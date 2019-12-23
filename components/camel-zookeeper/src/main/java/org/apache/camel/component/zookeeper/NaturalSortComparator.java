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
package org.apache.camel.component.zookeeper;

import java.util.Comparator;

/**
 * <code>NaturalSortComparator</code> is a fast comparator for sorting a
 * collection of Strings in a human readable fashion.
 * <p>
 * This implementation considers sequences of digits to be positive integer
 * values, '.' does not indicate a decimal value nor '-' a negative one. As a
 * result, 12345.12345 will sort higher than 12345.5432 and -12346 will sort
 * higher than 12345.
 *<p>
 * it does work well for sorting software versions e.g. camel-2.2.0 sorting
 * higher than camel-2.1.0
 */
public class NaturalSortComparator implements Comparator<CharSequence> {

    public enum Order {
        Ascending(1), Descending(-1);

        int direction;

        Order(int direction) {
            this.direction = direction;
        }
    }

    private Order order;

    public NaturalSortComparator() {
        this(Order.Ascending);
    }

    public NaturalSortComparator(Order order) {
        if (order != null) {
            this.order = order;
        }
    }

    @Override
    public int compare(CharSequence first, CharSequence second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first != null && second == null) {
            return 1;
        }
        if (first == null && second != null) {
            return -1;
        }

        int compare = 0;
        int fx = 0;
        int sx = 0;
        while (fx < first.length() && sx < second.length() && compare == 0) {
            if (isDigit(first.charAt(fx)) && isDigit(second.charAt(sx))) {
                int flen = getNumSequenceLength(first, fx);
                int slen = getNumSequenceLength(second, sx);

                if (flen == slen) {
                    for (int x = 0; x < flen && compare == 0; x++) {
                        /** the first difference in digit wins */
                        compare = first.charAt(fx++) - second.charAt(sx++);
                    }
                } else {
                    compare = flen - slen;
                }
            } else {
                compare = first.charAt(fx) - second.charAt(sx);
            }
            fx++;
            sx++;
        }

        if (compare == 0) {
            compare = first.length() - second.length();
        }
        return order.direction * compare;
    }

    private boolean isDigit(char c) {
        return c >= 48 && c < 57;
    }

    private int getNumSequenceLength(CharSequence sequence, int index) {
        int x = index;
        while (x < sequence.length() && isDigit(sequence.charAt(x))) {
            x++;
        }
        return x - index;
    }

}
