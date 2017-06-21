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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Tests loading type converters from classpath scanning.
 */
public class LoadTypeConvertersTest {

    @Test
    public void launchBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
            // Specify which benchmarks to run.
            // You can be more specific if you'd like to run only one benchmark per test.
            .include(this.getClass().getName() + ".*")
            // Set the following options as needed
            .mode(Mode.All)
            .timeUnit(TimeUnit.MICROSECONDS)
            .warmupTime(TimeValue.seconds(1))
            .warmupIterations(2)
            .measurementTime(TimeValue.seconds(1))
            .measurementIterations(2)
            .threads(2)
            .forks(1)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .build();

        new Runner(opt).run();
    }

    /**
     * Setup a fresh CamelContext per invocation
     */
    @State(Scope.Thread)
    public static class BenchmarkState {
        CamelContext camel;

        @Setup(Level.Invocation)
        public void initialize() {
            camel = new DefaultCamelContext();
        }

        @TearDown(Level.Invocation)
        public void close() {
            try {
                camel.stop();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Benchmark
    @Measurement(batchSize = 1000)
    public void load(BenchmarkState state, Blackhole bh) {
        int size = 0;
        try {
            state.camel.start();

            size = state.camel.getTypeConverterRegistry().size();
            bh.consume(size);
        } catch (Exception e) {
            // ignore
        }

        if (size < 200) {
            throw new IllegalArgumentException("Should have 200+ type converters loaded");
        }
    }

    @Benchmark
    @Measurement(batchSize = 1000)
    public void notLoad(BenchmarkState state, Blackhole bh) {
        int size = 0;
        try {
            state.camel.setLoadTypeConverters(false);
            state.camel.start();

            size = state.camel.getTypeConverterRegistry().size();
            bh.consume(size);
        } catch (Exception e) {
            // ignore
        }

        if (size > 200) {
            throw new IllegalArgumentException("Should not load additional type converters from classpath");
        }
    }

}
