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
package org.apache.camel.itest.jmh;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.util.CaseInsensitiveMap;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

/**
 * Tests {@link CaseInsensitiveMap}
 */
public class CaseInsensitiveMapTest {

    @Test
    public void launchBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
            // Specify which benchmarks to run.
            // You can be more specific if you'd like to run only one benchmark per test.
            .include(this.getClass().getName() + ".*")
            // Set the following options as needed
            .mode(Mode.SampleTime)
            .timeUnit(TimeUnit.MILLISECONDS)
            .warmupTime(TimeValue.seconds(1))
            .warmupIterations(2)
            .measurementTime(TimeValue.seconds(5))
            .measurementIterations(5)
            .threads(1)
            .forks(1)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .measurementBatchSize(1000000)
            .build();

        new Runner(opt).run();
    }

    // The JMH samples are the best documentation for how to use it
    // http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
    @State(Scope.Thread)
    public static class MapsBenchmarkState {
        CaseInsensitiveMap camelMap;
        com.cedarsoftware.util.CaseInsensitiveMap cedarsoftMap;
        HashMap hashMap;

        @Setup(Level.Trial)
        public void initialize() {
            camelMap = new CaseInsensitiveMap();
            cedarsoftMap = new com.cedarsoftware.util.CaseInsensitiveMap();
            hashMap = new HashMap();
        }

    }

    @State(Scope.Benchmark)
    public static class MapsSourceDataBenchmarkState {
        Map<String, Object> map1 = generateRandomMap(10);
        Map<String, Object> map2 = generateRandomMap(10);

        private Map<String, Object> generateRandomMap(int size) {
            return IntStream.range(0, size)
                    .boxed()
                    .collect(Collectors.toMap(i -> randomAlphabetic(10), i-> randomAlphabetic(10)));
        }
    }

    @Benchmark
    public void camelMapSimpleCase(MapsBenchmarkState state, Blackhole bh) {
        Map map = state.camelMap;

        map.put("foo", "Hello World");
        Object o1 = map.get("foo");
        bh.consume(o1);
        Object o2 = map.get("FOO");
        bh.consume(o2);

        map.put("BAR", "Bye World");
        Object o3 = map.get("bar");
        bh.consume(o3);
        Object o4 = map.get("BAR");
        bh.consume(o4);
    }

    @Benchmark
    public void cedarsoftMapSimpleCase(MapsBenchmarkState state, Blackhole bh) {
        Map map = state.cedarsoftMap;

        map.put("foo", "Hello World");
        Object o1 = map.get("foo");
        bh.consume(o1);
        Object o2 = map.get("FOO");
        bh.consume(o2);

        map.put("BAR", "Bye World");
        Object o3 = map.get("bar");
        bh.consume(o3);
        Object o4 = map.get("BAR");
        bh.consume(o4);
    }

    @Benchmark
    public void hashMapSimpleCase(MapsBenchmarkState state, Blackhole bh) {
        Map map = state.hashMap;

        map.put("foo", "Hello World");
        Object o1 = map.get("foo");
        bh.consume(o1);
        Object o2 = map.get("FOO");
        bh.consume(o2);

        map.put("BAR", "Bye World");
        Object o3 = map.get("bar");
        bh.consume(o3);
        Object o4 = map.get("BAR");
        bh.consume(o4);
    }

    @Benchmark
    public void camelMapComplexCase(MapsBenchmarkState mapsBenchmarkState, MapsSourceDataBenchmarkState sourceDataState, Blackhole blackhole) {
        // step 1 - initialize map with existing elements
        Map map = mapsBenchmarkState.camelMap;

        // step 2 - add elements one by one
        sourceDataState.map2.entrySet().forEach(entry -> blackhole.consume(map.put(entry.getKey(), entry.getValue())));

        // step 3 - remove elements one by one
        sourceDataState.map1.keySet().forEach(key -> blackhole.consume(map.get(key)));

        // step 4 - remove elements one by one
        sourceDataState.map1.keySet().forEach(key -> blackhole.consume(map.remove(key)));

        // step 5 - add couple of element at once
        map.putAll(sourceDataState.map1);

        blackhole.consume(map);
    }


    @Benchmark
    public void cedarsoftMapComplexCase(MapsBenchmarkState mapsBenchmarkState, MapsSourceDataBenchmarkState sourceDataState, Blackhole blackhole) {
        // step 1 - initialize map with existing elements
        Map map = mapsBenchmarkState.cedarsoftMap;

        // step 2 - add elements one by one
        sourceDataState.map2.entrySet().forEach(entry -> blackhole.consume(map.put(entry.getKey(), entry.getValue())));

        // step 3 - remove elements one by one
        sourceDataState.map1.keySet().forEach(key -> blackhole.consume(map.get(key)));

        // step 4 - remove elements one by one
        sourceDataState.map1.keySet().forEach(key -> blackhole.consume(map.remove(key)));

        // step 5 - add couple of element at once
        map.putAll(sourceDataState.map1);

        blackhole.consume(map);
    }

    @Benchmark
    public void hashMapComplexCase(MapsBenchmarkState mapsBenchmarkState, MapsSourceDataBenchmarkState sourceDataState, Blackhole blackhole) {
        // step 1 - initialize map with existing elements
        Map map = mapsBenchmarkState.hashMap;

        // step 2 - add elements one by one
        sourceDataState.map2.entrySet().forEach(entry -> blackhole.consume(map.put(entry.getKey(), entry.getValue())));

        // step 3 - remove elements one by one
        sourceDataState.map1.keySet().forEach(key -> blackhole.consume(map.get(key)));

        // step 4 - remove elements one by one
        sourceDataState.map1.keySet().forEach(key -> blackhole.consume(map.remove(key)));

        // step 5 - add couple of element at once
        map.putAll(sourceDataState.map1);

        blackhole.consume(map);
    }

}
