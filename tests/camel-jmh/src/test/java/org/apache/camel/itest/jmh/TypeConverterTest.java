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
package org.apache.camel.itest.jmh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
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
 * Tests {@link org.apache.camel.TypeConverter}
 */
public class TypeConverterTest {

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
                .measurementIterations(3)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .measurementBatchSize(100000)
                .build();

        new Runner(opt).run();
    }

    // The JMH samples are the best documentation for how to use it
    // http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
    @State(Scope.Thread)
    public static class BenchmarkCamelContextState {
        Integer someInteger = 12345;
        String someIntegerString = String.valueOf(someInteger);
        String xmlAsString;
        byte[] xmlAsBytes;

        CamelContext camel;

        @Setup(Level.Trial)
        public void initialize() throws IOException {
            camel = new DefaultCamelContext();
            try {
                camel.start();
            } catch (Exception e) {
                // ignore
            }

            xmlAsString = IOHelper.loadText(getClass().getClassLoader().getResourceAsStream("sample_soap.xml"));
            xmlAsBytes = xmlAsString.getBytes(StandardCharsets.UTF_8);
        }

        @TearDown(Level.Trial)
        public void close() {
            try {
                camel.stop();
            } catch (Exception e) {
                // ignore
            }
        }
    }


    @Benchmark
    public void typeConvertIntegerToString(BenchmarkCamelContextState state, Blackhole bh) {
        String string = state.camel.getTypeConverter().convertTo(String.class, state.someInteger);
        bh.consume(string);
    }

    @Benchmark
    public void typeConvertStringToInteger(BenchmarkCamelContextState state, Blackhole bh) {
        Integer integer = state.camel.getTypeConverter().convertTo(Integer.class, state.someIntegerString);
        bh.consume(integer);
    }

    @Benchmark
    public void typeConvertTheSameTypes(BenchmarkCamelContextState state, Blackhole bh) {
        String string = state.camel.getTypeConverter().convertTo(String.class, state.someIntegerString);
        bh.consume(string);
    }

    @Benchmark
    public void typeConvertInputStreamToString(BenchmarkCamelContextState state, Blackhole bh) {
        String string = state.camel.getTypeConverter().convertTo(String.class, new ByteArrayInputStream(state.xmlAsBytes));
        bh.consume(string);
    }

    @Benchmark
    public void typeConvertStringToInputStream(BenchmarkCamelContextState state, Blackhole bh) {
        InputStream inputStream = state.camel.getTypeConverter().convertTo(InputStream.class, state.xmlAsString);
        bh.consume(inputStream);
    }

    @Benchmark
    public void typeConvertStringToDocument(BenchmarkCamelContextState state, Blackhole bh) {
        Document document = state.camel.getTypeConverter().convertTo(Document.class, state.xmlAsString);
        bh.consume(document);
    }

    @Benchmark
    public void typeConvertStringToByteArray(BenchmarkCamelContextState state, Blackhole bh) {
        byte[] bytes = state.camel.getTypeConverter().convertTo(byte[].class, state.xmlAsString);
        bh.consume(bytes);
    }

    @Benchmark
    public void typeConvertByteArrayToString(BenchmarkCamelContextState state, Blackhole bh) {
        String string = state.camel.getTypeConverter().convertTo(String.class, state.xmlAsBytes);
        bh.consume(string);
    }
}
