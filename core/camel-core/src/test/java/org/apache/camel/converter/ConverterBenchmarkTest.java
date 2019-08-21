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
package org.apache.camel.converter;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinderResolver;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.util.ReflectionInjector;
import org.junit.Ignore;
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

public class ConverterBenchmarkTest {

    @Ignore
    @Test
    public void launchBenchmark() throws Exception {

        Options opt = new OptionsBuilder()
            // Specify which benchmarks to run.
            // You can be more specific if you'd like to run only one benchmark
            // per test.
            .include(this.getClass().getName() + ".*")
            // Set the following options as needed
            .mode(Mode.AverageTime).timeUnit(TimeUnit.MICROSECONDS).warmupTime(TimeValue.seconds(2)).warmupIterations(5).measurementTime(TimeValue.seconds(1))
            .measurementIterations(5).threads(2).forks(1).shouldFailOnError(true).shouldDoGC(true)
            // .jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
            // .addProfiler(WinPerfAsmProfiler.class)
            .build();

        new Runner(opt).run();
    }

    // The JMH samples are the best documentation for how to use it
    // http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
    @State(Scope.Thread)
    public static class BenchmarkState {
        DefaultPackageScanClassResolver packageScanClassResolver;
        Injector injector;
        FactoryFinder factoryFinder;
        DefaultTypeConverter converter;

        @Setup(Level.Trial)
        public void initialize() throws Exception {
            packageScanClassResolver = new DefaultPackageScanClassResolver();
            injector = new ReflectionInjector();
            factoryFinder = new DefaultFactoryFinderResolver().resolveDefaultFactoryFinder(new DefaultClassResolver());
            converter = new DefaultTypeConverter(packageScanClassResolver, injector, factoryFinder, true);
            converter.start();
        }
    }

    @Benchmark
    public void benchmarkLoadTime(BenchmarkState state, Blackhole bh) throws Exception {

        DefaultPackageScanClassResolver packageScanClassResolver = state.packageScanClassResolver;
        Injector injector = state.injector;
        FactoryFinder factoryFinder = state.factoryFinder;

        DefaultTypeConverter converter = new DefaultTypeConverter(packageScanClassResolver, injector, factoryFinder, true);
        converter.start();
        bh.consume(converter);
    }

    @Benchmark
    public void benchmarkConversionTimeEnum(BenchmarkState state, Blackhole bh) {
        DefaultTypeConverter converter = state.converter;

        for (int i = 0; i < 1000; i++) {
            bh.consume(converter.convertTo(LoggingLevel.class, "DEBUG"));
        }
    }

    @Benchmark
    public void benchmarkConversionIntToLong(BenchmarkState state, Blackhole bh) {
        DefaultTypeConverter converter = state.converter;

        for (int i = 0; i < 1000; i++) {
            bh.consume(converter.convertTo(Long.class, 3));
        }
    }

    @Benchmark
    public void benchmarkConversionStringToChar(BenchmarkState state, Blackhole bh) {
        DefaultTypeConverter converter = state.converter;

        for (int i = 0; i < 1000; i++) {
            bh.consume(converter.convertTo(char[].class, "Hello world"));
        }
    }

    @Benchmark
    public void benchmarkConversionStringToURI(BenchmarkState state, Blackhole bh) {
        DefaultTypeConverter converter = state.converter;

        for (int i = 0; i < 1000; i++) {
            bh.consume(converter.convertTo(URI.class, "uri:foo"));
        }
    }

    @Benchmark
    public void benchmarkConversionListToStringArray(BenchmarkState state, Blackhole bh) {
        DefaultTypeConverter converter = state.converter;

        for (int i = 0; i < 1000; i++) {
            bh.consume(converter.convertTo(String[].class, Arrays.asList("DEBUG")));
        }
    }

    @Ignore
    @Test
    public void testConvertEnumPerfs() throws Exception {
        Blackhole bh = new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
        BenchmarkState state = new BenchmarkState();
        state.initialize();
        doTest(bh, state);
    }

    private void doTest(Blackhole bh, BenchmarkState state) {
        DefaultTypeConverter converter = state.converter;
        for (int i = 0; i < 1000000; i++) {
            bh.consume(converter.convertTo(Long.class, 3));
        }
    }
}
