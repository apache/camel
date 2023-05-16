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
package org.apache.camel.processor.resequencer;

import java.util.TreeSet;

/**
 * A sorted set of elements with additional methods for obtaining immediate successors and immediate predecessors of a
 * given element in the sequence. Successors and predecessors are calculated by using a
 * {@link SequenceElementComparator}.
 */
public class Sequence<E> extends TreeSet<E> {

    private static final long serialVersionUID = 5647393631147741711L;

    private final SequenceElementComparator<E> comparator;

    /**
     * Creates a new {@link Sequence} instance.
     *
     * @param comparator a strategy for comparing elements of this sequence.
     */
    public Sequence(SequenceElementComparator<E> comparator) {
        super(comparator);
        this.comparator = comparator;
    }

    /**
     * Returns the immediate predecessor of the given element in this sequence or <code>null</code> if no predecessor
     * exists.
     *
     * @param  e an element which is compared to elements of this sequence.
     * @return   an element of this sequence or <code>null</code>.
     */
    public E predecessor(E e) {
        E elem = lower(e);
        if (elem == null) {
            return null;
        }
        if (comparator.predecessor(elem, e)) {
            return elem;
        }
        return null;
    }

    /**
     * Returns the immediate successor of the given element in this sequence or <code>null</code> if no successor
     * exists.
     *
     * @param  e an element which is compared to elements of this sequence.
     * @return   an element of this sequence or <code>null</code>.
     */
    public E successor(E e) {
        E elem = higher(e);
        if (elem == null) {
            return null;
        }
        if (comparator.successor(elem, e)) {
            return elem;
        }
        return null;
    }

    /**
     * Returns this sequence's comparator.
     *
     * @return this sequence's comparator.
     */
    @Override
    public SequenceElementComparator<E> comparator() {
        return comparator;
    }

    /**
     * Returns the next higher element in the sequence to the given element. If the given element doesn't exist or if it
     * is the last element in the sequence <code>null</code> is returned. <strong>Please note that this method is
     * provided for compatibility with Java 5 SE. On a Java 6 SE platform the same method implemented by the
     * {@link TreeSet} class should be used for better performance.</strong>
     *
     * @param  e an element which is compared to elements of this sequence.
     * @return   an element of this sequence or <code>null</code>.
     */
    @Override
    public E higher(E e) {
        boolean found = false;
        for (E current : this) {
            if (found) {
                return current;
            }
            if (comparator.compare(e, current) == 0) {
                found = true;
            }
        }
        return null;
    }

    /**
     * Returns the next lower element in the sequence to the given element. If the given element doesn't exist or if it
     * is the first element in the sequence <code>null</code> is returned. <strong>Please note that this method is
     * provided for compatibility with Java 5 SE. On a Java 6 SE platform the same method implemented by the
     * {@link TreeSet} class should be used for better performance.</strong>
     *
     * @param  e an element which is compared to elements of this sequence.
     * @return   an element of this sequence or <code>null</code>.
     */
    @Override
    public E lower(E e) {
        E last = null;
        for (E current : this) {
            if (comparator.compare(e, current) == 0) {
                return last;
            }
            last = current;
        }
        return last;
    }

}
