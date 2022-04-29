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

package org.apache.camel.processor.resume;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.apache.camel.resume.ResumeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A delegating adapter that can be used to delegate to and/or abstract resume adapters
 */
public class DelegatingResumeAdapter implements ResumeAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DelegatingResumeAdapter.class);

    private final List<ResumeAdapter> resumeStrategies;

    public DelegatingResumeAdapter() {
        resumeStrategies = new ArrayList<>();
    }

    protected DelegatingResumeAdapter(List<ResumeAdapter> resumeStrategies) {
        this.resumeStrategies = resumeStrategies;
    }

    public boolean add(ResumeAdapter resumeAdapter) {
        return resumeStrategies.add(resumeAdapter);
    }

    public boolean remove(Object resumeAdapter) {
        return resumeStrategies.remove(resumeAdapter);
    }

    public boolean removeIf(Predicate<? super ResumeAdapter> filter) {
        return resumeStrategies.removeIf(filter);
    }

    @Override
    public void resume() {
        resumeStrategies.forEach(ResumeAdapter::resume);
    }

    public int size() {
        return resumeStrategies.size();
    }

    public boolean isEmpty() {
        return resumeStrategies.isEmpty();
    }

    public boolean contains(Object o) {
        return resumeStrategies.contains(o);
    }

    public Iterator<ResumeAdapter> iterator() {
        return resumeStrategies.iterator();
    }

    public Object[] toArray() {
        return resumeStrategies.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return resumeStrategies.toArray(a);
    }

    public boolean containsAll(Collection<?> c) {
        return resumeStrategies.containsAll(c);
    }

    public boolean addAll(Collection<? extends ResumeAdapter> c) {
        return resumeStrategies.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends ResumeAdapter> c) {
        return resumeStrategies.addAll(index, c);
    }

    public boolean removeAll(Collection<?> c) {
        return resumeStrategies.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return resumeStrategies.retainAll(c);
    }

    public void replaceAll(UnaryOperator<ResumeAdapter> operator) {
        resumeStrategies.replaceAll(operator);
    }

    public void sort(Comparator<? super ResumeAdapter> c) {
        resumeStrategies.sort(c);
    }

    public void clear() {
        resumeStrategies.clear();
    }

    public ResumeAdapter get(int index) {
        return resumeStrategies.get(index);
    }

    public ResumeAdapter set(int index, ResumeAdapter element) {
        return resumeStrategies.set(index, element);
    }

    public void add(int index, ResumeAdapter element) {
        resumeStrategies.add(index, element);
    }

    public ResumeAdapter remove(int index) {
        return resumeStrategies.remove(index);
    }

    public int indexOf(Object o) {
        return resumeStrategies.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return resumeStrategies.lastIndexOf(o);
    }

    public ListIterator<ResumeAdapter> listIterator() {
        return resumeStrategies.listIterator();
    }

    public ListIterator<ResumeAdapter> listIterator(int index) {
        return resumeStrategies.listIterator(index);
    }

    public List<ResumeAdapter> subList(int fromIndex, int toIndex) {
        return resumeStrategies.subList(fromIndex, toIndex);
    }

    public Spliterator<ResumeAdapter> spliterator() {
        return resumeStrategies.spliterator();
    }

    public <T> T[] toArray(IntFunction<T[]> generator) {
        return resumeStrategies.toArray(generator);
    }

    public Stream<ResumeAdapter> stream() {
        return resumeStrategies.stream();
    }

    public Stream<ResumeAdapter> parallelStream() {
        return resumeStrategies.parallelStream();
    }

    public void forEach(Consumer<? super ResumeAdapter> action) {
        resumeStrategies.forEach(action);
    }
}
