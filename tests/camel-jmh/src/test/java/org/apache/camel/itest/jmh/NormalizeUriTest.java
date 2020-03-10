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

import java.util.concurrent.TimeUnit;

import org.apache.camel.util.URISupport;
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

/**
 * Tests the {@link org.apache.camel.util.URISupport#normalizeUri(String)}.
 * <p/>
 * Thanks to this SO answer: https://stackoverflow.com/questions/30485856/how-to-run-jmh-from-inside-junit-tests
 */
public class NormalizeUriTest {

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
            .shouldDoGC(false)
            .measurementBatchSize(100000)
            .build();

        new Runner(opt).run();
    }

    // The JMH samples are the best documentation for how to use it
    // http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
    @State(Scope.Thread)
    public static class BenchmarkState {
        @Setup(Level.Trial)
        public void initialize() {
        }
    }

    @Benchmark
    public void benchmarkMixed(ContainsIgnoreCaseTest.BenchmarkState state, Blackhole bh) throws Exception {
        // fast
        bh.consume(URISupport.normalizeUri("log:foo?level=INFO&logMask=false&exchangeFormatter=#myFormatter"));
        // slow
        bh.consume(URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren%20Hansen"));
        // fast
        bh.consume(URISupport.normalizeUri("file:target/inbox?recursive=true"));
        // slow
        bh.consume(URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren%20Hansen"));
        // fast
        bh.consume(URISupport.normalizeUri("seda:foo?concurrentConsumer=2"));
        // slow
        bh.consume(URISupport.normalizeUri("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us@r"));
        // fast
        bh.consume(URISupport.normalizeUri("http:www.google.com?q=Camel"));
        // slow
        bh.consume(URISupport.normalizeUri("ftp://us@r:t%25st@localhost:21000/tmp3/camel?foo=us@r"));
    }

    @Benchmark
    public void benchmarkFast(ContainsIgnoreCaseTest.BenchmarkState state, Blackhole bh) throws Exception {
        bh.consume(URISupport.normalizeUri("log:foo"));
        bh.consume(URISupport.normalizeUri("log:foo?level=INFO&logMask=false&exchangeFormatter=#myFormatter"));
        bh.consume(URISupport.normalizeUri("file:target/inbox?recursive=true"));
        bh.consume(URISupport.normalizeUri("smtp://localhost?password=secret&username=davsclaus"));
        bh.consume(URISupport.normalizeUri("seda:foo?concurrentConsumer=2"));
        bh.consume(URISupport.normalizeUri("irc:someserver/#camel?user=davsclaus"));
        bh.consume(URISupport.normalizeUri("http:www.google.com?q=Camel"));
        bh.consume(URISupport.normalizeUri("smtp://localhost?to=foo&to=bar&from=me&from=you"));
    }

    @Benchmark
    public void benchmarkFastSorted(ContainsIgnoreCaseTest.BenchmarkState state, Blackhole bh) throws Exception {
        bh.consume(URISupport.normalizeUri("log:foo"));
        bh.consume(URISupport.normalizeUri("log:foo?exchangeFormatter=#myFormatter&level=INFO&logMask=false"));
        bh.consume(URISupport.normalizeUri("file:target/inbox?recursive=true"));
        bh.consume(URISupport.normalizeUri("smtp://localhost?username=davsclaus&password=secret"));
        bh.consume(URISupport.normalizeUri("seda:foo?concurrentConsumer=2"));
        bh.consume(URISupport.normalizeUri("irc:someserver/#camel?user=davsclaus"));
        bh.consume(URISupport.normalizeUri("http:www.google.com?q=Camel"));
        bh.consume(URISupport.normalizeUri("smtp://localhost?&from=me&from=you&to=foo&to=bar"));
    }

    @Benchmark
    public void benchmarkSlow(ContainsIgnoreCaseTest.BenchmarkState state, Blackhole bh) throws Exception {
        bh.consume(URISupport.normalizeUri("http://www.google.com?q=S%C3%B8ren%20Hansen"));
        bh.consume(URISupport.normalizeUri("ftp://us%40r:t%st@localhost:21000/tmp3/camel?foo=us@r"));
        bh.consume(URISupport.normalizeUri("ftp://us%40r:t%25st@localhost:21000/tmp3/camel?foo=us@r"));
        bh.consume(URISupport.normalizeUri("ftp://us@r:t%st@localhost:21000/tmp3/camel?foo=us@r"));
        bh.consume(URISupport.normalizeUri("ftp://us@r:t%25st@localhost:21000/tmp3/camel?foo=us@r"));
        bh.consume(URISupport.normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=secret&serviceName=someCoolChat"));
        bh.consume(URISupport.normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(++?w0rd)&serviceName=some chat"));
        bh.consume(URISupport.normalizeUri("xmpp://camel-user@localhost:123/test-user@localhost?password=RAW(foo %% bar)&serviceName=some chat"));
    }

    @Benchmark
    public void sorting(ContainsIgnoreCaseTest.BenchmarkState state, Blackhole bh) throws Exception {
        bh.consume(URISupport.normalizeUri("log:foo?zzz=123&xxx=222&hhh=444&aaa=tru&d=yes&cc=no&Camel=awesome&foo.hey=bar&foo.bar=blah"));
    }

}
