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
package org.apache.camel.test.dst.sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Code under test that handles a collection with a parallel stream (on the common fork-join pool unless rewritten).
 */
public final class SampleStreams {

    private SampleStreams() {
    }

    /** The names of the threads that handled the elements, in the order they were handled. */
    public static List<String> threadsOfParallelStream(int elements) {
        List<Integer> values = IntStream.range(0, elements).boxed().collect(Collectors.toList());
        List<String> threads = Collections.synchronizedList(new ArrayList<>());
        values.parallelStream().forEach(v -> threads.add(v + "@" + Thread.currentThread().getName()));
        return threads;
    }
}
