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
package org.apache.camel.component.wasm;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.wasm.Wasm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import run.endive.runtime.TrapException;
import run.endive.wabt.Wat2Wasm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Runs small WASI Preview 1 command modules, written in WAT in this class and compiled when the tests run.
 */
class WasmComponentTest {

    /**
     * The start of every test module: the WASI functions the modules use, one page of memory, and two helpers. The
     * helpers keep their I/O vector at address 0 and the byte count at address 8.
     */
    private static final String PRELUDE = """
            (module
              (import "wasi_snapshot_preview1" "fd_read" (func $fd_read (param i32 i32 i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "proc_exit" (func $proc_exit (param i32)))
              (import "wasi_snapshot_preview1" "environ_sizes_get" (func $environ_sizes_get (param i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "environ_get" (func $environ_get (param i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "args_sizes_get" (func $args_sizes_get (param i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "args_get" (func $args_get (param i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "fd_fdstat_get" (func $fd_fdstat_get (param i32 i32) (result i32)))
              (memory (export "memory") 1)

              (func $write (param $fd i32) (param $ptr i32) (param $len i32)
                (i32.store (i32.const 0) (local.get $ptr))
                (i32.store (i32.const 4) (local.get $len))
                (drop (call $fd_write (local.get $fd) (i32.const 0) (i32.const 1) (i32.const 8))))

              (func $read (param $ptr i32) (param $len i32) (result i32)
                (i32.store (i32.const 0) (local.get $ptr))
                (i32.store (i32.const 4) (local.get $len))
                (drop (call $fd_read (i32.const 0) (i32.const 0) (i32.const 1) (i32.const 8)))
                (i32.load (i32.const 8)))
            """;

    /** Copies standard input to standard output, with ASCII letters in upper case. */
    private static final String UPPER = """
            (func (export "_start")
              (local $n i32) (local $i i32) (local $c i32)
              (block $done
                (loop $more
                  (local.set $n (call $read (i32.const 1024) (i32.const 4096)))
                  (br_if $done (i32.eqz (local.get $n)))
                  (local.set $i (i32.const 0))
                  (block $end
                    (loop $each
                      (br_if $end (i32.ge_u (local.get $i) (local.get $n)))
                      (local.set $c (i32.load8_u (i32.add (i32.const 1024) (local.get $i))))
                      (if (i32.and (i32.ge_u (local.get $c) (i32.const 97)) (i32.le_u (local.get $c) (i32.const 122)))
                        (then (i32.store8 (i32.add (i32.const 1024) (local.get $i))
                                          (i32.sub (local.get $c) (i32.const 32)))))
                      (local.set $i (i32.add (local.get $i) (i32.const 1)))
                      (br $each)))
                  (call $write (i32.const 1) (i32.const 1024) (local.get $n))
                  (br $more))))
            """;

    /** Writes "out" to standard output and "boom" to standard error, then exits with the given code. */
    private static final String EXIT = """
            (data (i32.const 16) "boom")
            (data (i32.const 32) "out")
            (func (export "_start")
              (call $write (i32.const 1) (i32.const 32) (i32.const 3))
              (call $write (i32.const 2) (i32.const 16) (i32.const 4))
              (call $proc_exit (i32.const %d)))
            """;

    /** Writes its environment to standard output: NAME=value entries, each followed by a NUL. */
    private static final String ENV = """
            (func (export "_start")
              (drop (call $environ_sizes_get (i32.const 100) (i32.const 104)))
              (drop (call $environ_get (i32.const 200) (i32.const 1024)))
              (call $write (i32.const 1) (i32.const 1024) (i32.load (i32.const 104))))
            """;

    /** Writes its arguments to standard output, each followed by a NUL. */
    private static final String ARGS = """
            (func (export "_start")
              (drop (call $args_sizes_get (i32.const 100) (i32.const 104)))
              (drop (call $args_get (i32.const 200) (i32.const 1024)))
              (call $write (i32.const 1) (i32.const 1024) (i32.load (i32.const 104))))
            """;

    /** Writes the WASI file type of its standard input, output and error as three digits. */
    private static final String FILE_TYPES = """
            (func $type (param $fd i32) (param $at i32)
              (drop (call $fd_fdstat_get (local.get $fd) (i32.const 100)))
              (i32.store8 (local.get $at) (i32.add (i32.const 48) (i32.load8_u (i32.const 100)))))
            (func (export "_start")
              (call $type (i32.const 0) (i32.const 1024))
              (call $type (i32.const 1) (i32.const 1025))
              (call $type (i32.const 2) (i32.const 1026))
              (call $write (i32.const 1) (i32.const 1024) (i32.const 3)))
            """;

    /** Never returns. */
    private static final String SPIN = """
            (func (export "_start")
              (loop $forever (br $forever)))
            """;

    /** Reads its standard input to the end, then never returns. */
    private static final String READ_SPIN = """
            (func (export "_start")
              (block $done
                (loop $more
                  (br_if $done (i32.eqz (call $read (i32.const 1024) (i32.const 4096))))
                  (br $more)))
              (loop $forever (br $forever)))
            """;

    /** Reads its standard input to the end, then exits with code 3. */
    private static final String READ_EXIT = """
            (func (export "_start")
              (block $done
                (loop $more
                  (br_if $done (i32.eqz (call $read (i32.const 1024) (i32.const 4096))))
                  (br $more)))
              (call $proc_exit (i32.const 3)))
            """;

    /**
     * Sleeps for an hour in the WASI poll_oneoff call: one subscription at address 0, to the monotonic clock (type 0 at
     * offset 8, clock 1 at offset 16) with a relative timeout in nanoseconds at offset 24.
     */
    private static final String SLEEP = """
            (module
              (import "wasi_snapshot_preview1" "poll_oneoff" (func $poll_oneoff (param i32 i32 i32 i32) (result i32)))
              (memory (export "memory") 1)
              (func (export "_start")
                (i32.store8 (i32.const 8) (i32.const 0))
                (i32.store (i32.const 16) (i32.const 1))
                (i64.store (i32.const 24) (i64.const 3600000000000))
                (drop (call $poll_oneoff (i32.const 0) (i32.const 64) (i32.const 1) (i32.const 128)))))
            """;

    /** Writes "panicked" to standard error, then traps, as a Rust program does on a panic. */
    private static final String TRAP = """
            (data (i32.const 16) "panicked")
            (func (export "_start")
              (call $write (i32.const 2) (i32.const 16) (i32.const 8))
              unreachable)
            """;

    /** Grows its memory one page at a time, at most 16 times, and exits with the number of pages it ended up with. */
    private static final String GROW = """
            (func (export "_start")
              (local $i i32)
              (block $stop
                (loop $grow
                  (br_if $stop (i32.ge_u (local.get $i) (i32.const 16)))
                  (br_if $stop (i32.eq (memory.grow (i32.const 1)) (i32.const -1)))
                  (local.set $i (i32.add (local.get $i) (i32.const 1)))
                  (br $grow)))
              (call $proc_exit (memory.size)))
            """;

    /** Writes 10 blocks of 100 bytes to the given file descriptor. */
    private static final String FLOOD = """
            (func (export "_start")
              (local $i i32)
              (block $done
                (loop $block
                  (br_if $done (i32.ge_u (local.get $i) (i32.const 10)))
                  (call $write (i32.const %d) (i32.const 1024) (i32.const 100))
                  (local.set $i (i32.add (local.get $i) (i32.const 1)))
                  (br $block))))
            """;

    /** Counts its runs in a global and writes the count as a digit. */
    private static final String COUNTER = """
            (global $count (mut i32) (i32.const 0))
            (func (export "_start")
              (global.set $count (i32.add (global.get $count) (i32.const 1)))
              (i32.store8 (i32.const 1024) (i32.add (i32.const 48) (global.get $count)))
              (call $write (i32.const 1) (i32.const 1024) (i32.const 1)))
            """;

    /** Writes 32 bytes of WASI random_get to standard output. */
    private static final String RANDOM = """
            (module
              (import "wasi_snapshot_preview1" "random_get" (func $random_get (param i32 i32) (result i32)))
              (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
              (memory (export "memory") 1)
              (func (export "_start")
                (drop (call $random_get (i32.const 1024) (i32.const 32)))
                (i32.store (i32.const 0) (i32.const 1024))
                (i32.store (i32.const 4) (i32.const 32))
                (drop (call $fd_write (i32.const 1) (i32.const 0) (i32.const 1) (i32.const 8)))))
            """;

    @TempDir
    static Path dir;

    /**
     * Compiles the prelude plus the given functions to a module file and returns its location for the endpoint URI.
     */
    private static String module(String name, String functions) throws Exception {
        return wat(name, PRELUDE + functions + ")");
    }

    private static String wat(String name, String wat) throws Exception {
        Path file = dir.resolve(name + ".wasm");
        Files.write(file, Wat2Wasm.parse(wat));
        return "file:" + file.toAbsolutePath();
    }

    private static CamelContext context(String uri) throws Exception {
        CamelContext cc = new DefaultCamelContext();
        cc.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").routeId("wasm").to(uri);
            }
        });
        return cc;
    }

    private static CamelContext start(String uri) throws Exception {
        CamelContext cc = context(uri);
        cc.start();
        return cc;
    }

    /**
     * Starts a context that hands a stream body to the endpoint as it is, instead of as a StreamCache that was already
     * read into memory when the exchange entered the route.
     */
    private static CamelContext startWithoutStreamCaching(String uri) throws Exception {
        CamelContext cc = context(uri);
        cc.setStreamCaching(false);
        cc.start();
        return cc;
    }

    private static void assertStartFails(String uri, String message) throws Exception {
        try (CamelContext cc = context(uri)) {
            assertThatThrownBy(cc::start)
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(message);
        }
    }

    private static Exchange send(CamelContext cc, Object body) {
        return send(cc.createFluentProducerTemplate().withBody(body));
    }

    private static Exchange send(FluentProducerTemplate pt) {
        return pt.to("direct:in").request(Exchange.class);
    }

    private static String text(Exchange exchange) {
        return new String(exchange.getMessage().getBody(byte[].class), StandardCharsets.UTF_8);
    }

    /**
     * Whether a thread of a wasm producer (named "... - Wasm" by Camel) is running guest code.
     */
    private static boolean moduleRunning() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().endsWith(" - Wasm"))
                .anyMatch(e -> Arrays.stream(e.getValue()).anyMatch(f -> f.getClassName().startsWith("run.endive.runtime")));
    }

    @Test
    void bodyIsStandardInputAndStandardOutputIsBody() throws Exception {
        try (CamelContext cc = start("wasm:" + module("upper", UPPER))) {
            Exchange out = send(cc.createFluentProducerTemplate()
                    .withBody("hello wasi".getBytes(StandardCharsets.UTF_8))
                    .withHeader("foo", "bar"));

            assertThat(out.getException()).isNull();
            assertThat(out.getMessage().getBody()).isInstanceOf(byte[].class);
            assertThat(text(out)).isEqualTo("HELLO WASI");
            assertThat(out.getMessage().getHeaders())
                    .containsEntry("foo", "bar")
                    .containsEntry(Wasm.Headers.EXIT_CODE, 0)
                    .doesNotContainKey(Wasm.Headers.STDERR);
        }
    }

    @Test
    void largeAndEmptyBodies() throws Exception {
        try (CamelContext cc = start("wasm:" + module("upper", UPPER))) {
            byte[] large = new byte[1024 * 1024];
            Arrays.fill(large, (byte) 'a');
            byte[] expected = new byte[large.length];
            Arrays.fill(expected, (byte) 'A');

            assertThat(send(cc, large).getMessage().getBody(byte[].class)).isEqualTo(expected);
            assertThat(send(cc, null).getMessage().getBody(byte[].class)).isEmpty();
        }
    }

    @Test
    void nonZeroExitFailsTheExchange() throws Exception {
        try (CamelContext cc = start("wasm:" + module("exit3", EXIT.formatted(3)))) {
            Exchange out = send(cc, "in");

            assertThat(out.getException())
                    .isInstanceOfSatisfying(WasmExitCodeException.class, e -> {
                        assertThat(e.getExitCode()).isEqualTo(3);
                        assertThat(e.getStderr()).isEqualTo("boom");
                    })
                    .hasMessageContaining("exited with code 3: boom");
            assertThat(out.getMessage().getBody(String.class)).isEqualTo("in");
            assertThat(out.getMessage().getHeaders())
                    .containsEntry(Wasm.Headers.EXIT_CODE, 3)
                    .containsEntry(Wasm.Headers.STDERR, "boom");
        }
    }

    @Test
    void nonZeroExitWithFailOnNonZeroExitFalse() throws Exception {
        try (CamelContext cc = start("wasm:" + module("exit3", EXIT.formatted(3)) + "?failOnNonZeroExit=false")) {
            Exchange out = send(cc, "in");

            assertThat(out.getException()).isNull();
            assertThat(text(out)).isEqualTo("out");
            assertThat(out.getMessage().getHeaders())
                    .containsEntry(Wasm.Headers.EXIT_CODE, 3)
                    .containsEntry(Wasm.Headers.STDERR, "boom");
        }
    }

    @Test
    void exitZeroWithStandardError() throws Exception {
        try (CamelContext cc = start("wasm:" + module("exit0", EXIT.formatted(0)))) {
            Exchange out = send(cc, "in");

            assertThat(out.getException()).isNull();
            assertThat(text(out)).isEqualTo("out");
            assertThat(out.getMessage().getHeaders())
                    .containsEntry(Wasm.Headers.EXIT_CODE, 0)
                    .containsEntry(Wasm.Headers.STDERR, "boom");
        }
    }

    @Test
    void resultHeadersSentWithTheMessageAreReplaced() throws Exception {
        try (CamelContext cc = start("wasm:" + module("upper", UPPER))) {
            Exchange out = send(cc.createFluentProducerTemplate()
                    .withBody("a")
                    .withHeader(Wasm.Headers.EXIT_CODE, 99)
                    .withHeader(Wasm.Headers.STDERR, "forged"));

            assertThat(out.getMessage().getHeaders())
                    .containsEntry(Wasm.Headers.EXIT_CODE, 0)
                    .doesNotContainKey(Wasm.Headers.STDERR);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "?timeout=30000" })
    void trapFailsTheExchangeWithStandardError(String options) throws Exception {
        try (CamelContext cc = start("wasm:" + module("trap", TRAP) + options)) {
            Exchange out = send(cc.createFluentProducerTemplate()
                    .withBody("in")
                    .withHeader(Wasm.Headers.EXIT_CODE, 99)
                    .withHeader(Wasm.Headers.STDERR, "forged"));

            assertThat(out.getException())
                    .isInstanceOf(CamelExchangeException.class)
                    .hasMessageContaining("trapped")
                    .hasMessageContaining(": panicked")
                    .hasCauseInstanceOf(TrapException.class);
            assertThat(out.getMessage().getBody(String.class)).isEqualTo("in");
            assertThat(out.getMessage().getHeaders())
                    .doesNotContainKey(Wasm.Headers.EXIT_CODE)
                    .containsEntry(Wasm.Headers.STDERR, "panicked");
        }
    }

    @Test
    void environmentOnlyFromTheRouteAndTheAllowListedHeaders() throws Exception {
        String uri = "wasm:" + module("env", ENV)
                     + "?environment.LEVEL=debug&environmentHeaders=TENANT,REGION";
        try (CamelContext cc = start(uri)) {
            Exchange out = send(cc.createFluentProducerTemplate()
                    .withBody("")
                    .withHeader("TENANT", "acme")
                    .withHeader("SECRET", "s3cr3t")
                    .withHeader("CamelHttpUri", "http://example"));

            // REGION is listed but not on the message, so it is not set; nothing from the JVM environment
            assertThat(text(out)).isEqualTo("LEVEL=debug\0TENANT=acme\0");
        }
    }

    @Test
    void headerWithNulCannotBePassed() throws Exception {
        try (CamelContext cc = start("wasm:" + module("env", ENV) + "?environmentHeaders=TENANT")) {
            Exchange out = send(cc.createFluentProducerTemplate().withBody("").withHeader("TENANT", "a\0b"));

            assertThat(out.getException())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("header TENANT contains a NUL");
        }
    }

    @Test
    void environmentNameConfiguredTwiceIsRejected() throws Exception {
        String uri = "wasm:" + module("env", ENV) + "?environment.TENANT=x&environmentHeaders=TENANT";

        assertStartFails(uri, "TENANT is configured both in environment and environmentHeaders");
    }

    @Test
    void argumentsFromTheRoute() throws Exception {
        String module = module("args", ARGS);
        try (CamelContext cc = start("wasm:" + module + "?args=--level 2 \"two words\"")) {
            Exchange out = send(cc, "");

            // argument zero is the file name of the module, not its location
            assertThat(module).startsWith("file:").endsWith("/args.wasm");
            assertThat(text(out)).isEqualTo("args.wasm\0--level\0" + "2\0two words\0");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "file:/opt/modules/upper.wasm, upper.wasm",
            "file:C:\\modules\\upper.wasm, upper.wasm",
            "classpath:upper.wasm, upper.wasm",
            "classpath:org/acme/upper.wasm, upper.wasm",
            "upper.wasm, upper.wasm",
            "ref:upperModule, upperModule",
            "https://user:secret@example.com/mods/upper.wasm?token=abc123, upper.wasm",
            "http://user:secret@example.com, module.wasm",
            "http://example.com:8080/, module.wasm",
            "base64:AGFzbQEAAAA=, module.wasm",
            "gzip:H4sIAAAA/abc, module.wasm" })
    void argumentZeroIsTheFileNameOfTheModule(String location, String expected) {
        assertThat(WasmProducer.programName(location)).isEqualTo(expected);
        assertThat(WasmProducer.arguments(WasmProducer.programName(location), "-v")).containsExactly(expected, "-v");
    }

    @Test
    void bodyThatIsNotAStreamFailsTheExchange() throws Exception {
        try (CamelContext cc = start("wasm:" + module("upper", UPPER))) {
            // a body without a converter to InputStream must not become empty standard input
            Exchange out = send(cc, new Object());

            assertThat(out.getException()).isInstanceOf(InvalidPayloadException.class);
            assertThat(out.getMessage().getHeaders()).doesNotContainKey(Wasm.Headers.EXIT_CODE);
        }
    }

    @Test
    void randomGetReturnsBytesOfTheHost() throws Exception {
        try (CamelContext cc = start("wasm:" + wat("random", RANDOM))) {
            byte[] first = send(cc, "").getMessage().getBody(byte[].class);
            byte[] second = send(cc, "").getMessage().getBody(byte[].class);

            assertThat(first).hasSize(32).isNotEqualTo(new byte[32]);
            assertThat(second).hasSize(32).isNotEqualTo(first);
        }
    }

    @Test
    void unbalancedQuoteInArgumentsIsRejected() throws Exception {
        String uri = "wasm:" + module("args", ARGS) + "?args=\"open";

        assertStartFails(uri, "Unbalanced double quote");
    }

    @Test
    void standardStreamsAreNotTerminals() throws Exception {
        try (CamelContext cc = start("wasm:" + module("filetypes", FILE_TYPES))) {
            // 0 is the WASI file type "unknown", which Endive reports for a stream that is not a terminal; a terminal
            // is 2 (character device)
            assertThat(text(send(cc, ""))).isEqualTo("000");
        }
    }

    @Test
    void timeoutInterruptsASpinningModule() throws Exception {
        try (CamelContext cc = start("wasm:" + module("spin", SPIN) + "?timeout=500")) {
            for (int i = 0; i < 2; i++) {
                Exchange out = send(cc, "in");

                assertThat(out.getException())
                        .isInstanceOf(ExchangeTimedOutException.class)
                        .hasMessageContaining("was interrupted");
                assertThat(out.getMessage().getHeaders())
                        .doesNotContainKey(Wasm.Headers.EXIT_CODE)
                        .doesNotContainKey(Wasm.Headers.STDERR);
            }

            // the interrupted modules stopped: no thread is left running guest code
            await().atMost(10, TimeUnit.SECONDS).until(() -> !moduleRunning());
        }
    }

    @Test
    void timeoutInterruptsAModuleSleepingInAHostCall() throws Exception {
        try (CamelContext cc = start("wasm:" + wat("sleep", SLEEP) + "?timeout=500")) {
            Exchange out = send(cc, "in");

            // a module whose sleep ended early would have exited with code 0
            assertThat(out.getException()).isInstanceOf(ExchangeTimedOutException.class);

            // the sleep ended on the interrupt: the thread of the producer is free again
            await().atMost(10, TimeUnit.SECONDS).until(() -> !moduleRunning());
        }
    }

    @Test
    void timeoutReadsTheBodyOnTheCallingThread() throws Exception {
        try (CamelContext cc = startWithoutStreamCaching("wasm:" + module("upper", UPPER) + "?timeout=30000")) {
            ObservedStream body = new ObservedStream("hello wasi", false);

            Exchange out = send(cc, body);

            assertThat(out.getException()).isNull();
            assertThat(text(out)).isEqualTo("HELLO WASI");
            // the module got a copy: the stream of the route was never read by a thread of the producer
            assertThat(body.readers).containsExactly(Thread.currentThread());
        }
    }

    @Test
    void timeoutWithABodyThatBlocksKeepsTheProducerThreadsOffTheStream() throws Exception {
        ObservedStream body = new ObservedStream("hello wasi", true);
        ExecutorService caller = Executors.newSingleThreadExecutor(r -> new Thread(r, "caller"));
        try (CamelContext cc = startWithoutStreamCaching("wasm:" + module("upper", UPPER) + "?timeout=30000")) {
            try {
                Future<Exchange> result = caller.submit(() -> send(cc, body));

                // a thread is blocked in a read of the body, which an interrupt does not end: it is the caller, and
                // not a thread of the producer that a timeout would leave behind on a stream the route uses again
                assertThat(body.reading.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(body.readers).extracting(Thread::getName).containsExactly("caller");

                body.release.countDown();
                Exchange out = result.get(30, TimeUnit.SECONDS);

                assertThat(out.getException()).isNull();
                assertThat(text(out)).isEqualTo("HELLO WASI");
                assertThat(body.readers).extracting(Thread::getName).containsExactly("caller");
            } finally {
                // never leave a reader blocked, and release it before the context stops
                body.release.countDown();
                caller.shutdownNow();
            }
        }
    }

    @Test
    void timedOutModuleLeavesNoThreadReadingTheBody() throws Exception {
        try (CamelContext cc = startWithoutStreamCaching("wasm:" + module("readspin", READ_SPIN) + "?timeout=500")) {
            ObservedStream body = new ObservedStream("hello wasi", false);

            Exchange out = send(cc, body);

            assertThat(out.getException()).isInstanceOf(ExchangeTimedOutException.class);
            int reads = body.reads.get();
            assertThat(body.readers).containsExactly(Thread.currentThread());
            assertThat(body.inRead).hasValue(0);
            // the body was a stream that is used up: it is replaced by what was read from it
            assertThat(out.getMessage().getBody()).isEqualTo("hello wasi".getBytes(StandardCharsets.UTF_8));

            // and the module that was left behind ends without touching the stream
            await().atMost(10, TimeUnit.SECONDS).until(() -> !moduleRunning());
            assertThat(body.reads).hasValue(reads);
        }
    }

    @Test
    void streamBodyAfterAFailedRunWithoutStreamCaching() throws Exception {
        String module = module("readexit", READ_EXIT);
        try (CamelContext cc = startWithoutStreamCaching("wasm:" + module)) {
            InputStream body = new ByteArrayInputStream("in".getBytes(StandardCharsets.UTF_8));

            Exchange out = send(cc, body);

            // without a timeout the module read the stream itself: the body is the same stream, at its end
            assertThat(out.getException()).isInstanceOf(WasmExitCodeException.class);
            assertThat(out.getMessage().getBody()).isSameAs(body);
            assertThat(body.read()).isEqualTo(-1);
        }
        try (CamelContext cc = startWithoutStreamCaching("wasm:" + module + "?timeout=30000")) {
            Exchange out = send(cc, new ByteArrayInputStream("in".getBytes(StandardCharsets.UTF_8)));

            // with a timeout the body was read into memory first, and that copy is the body after the failure
            assertThat(out.getException()).isInstanceOf(WasmExitCodeException.class);
            assertThat(out.getMessage().getBody()).isEqualTo("in".getBytes(StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "?timeout=30000" })
    void streamBodyAfterAFailedRunWithStreamCaching(String options) throws Exception {
        String uri = "wasm:" + module("readexit", READ_EXIT) + options;
        AtomicReference<String> seen = new AtomicReference<>();
        try (CamelContext cc = new DefaultCamelContext()) {
            cc.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    onException(WasmExitCodeException.class).handled(true)
                            .process(e -> seen.set(e.getMessage().getBody(String.class)));
                    from("direct:in").to(uri);
                }
            });
            cc.start();

            send(cc, new ByteArrayInputStream("in".getBytes(StandardCharsets.UTF_8)));

            // stream caching is on by default: the module read the cached stream, and Camel reset it for the handler
            assertThat(seen).hasValue("in");
        }
    }

    @Test
    void timeoutAcrossARouteRestart() throws Exception {
        try (CamelContext cc = start("wasm:" + module("upper", UPPER) + "?timeout=30000")) {
            assertThat(text(send(cc, "before"))).isEqualTo("BEFORE");

            cc.getRouteController().stopRoute("wasm");
            cc.getRouteController().startRoute("wasm");

            assertThat(text(send(cc, "after"))).isEqualTo("AFTER");
        }
    }

    @Test
    void maxMemoryPagesLimitsMemoryGrowth() throws Exception {
        String module = module("grow", GROW);
        try (CamelContext cc = start("wasm:" + module + "?failOnNonZeroExit=false&maxMemoryPages=4")) {
            assertThat(send(cc, "").getMessage().getHeader(Wasm.Headers.EXIT_CODE)).isEqualTo(4);
        }
        try (CamelContext cc = start("wasm:" + module + "?failOnNonZeroExit=false")) {
            assertThat(send(cc, "").getMessage().getHeader(Wasm.Headers.EXIT_CODE)).isEqualTo(17);
        }
    }

    @Test
    void maxMemoryPagesBelowTheInitialMemoryIsRejected() throws Exception {
        String uri = "wasm:" + wat("big", "(module (memory 2) (func (export \"_start\")))") + "?maxMemoryPages=1";

        assertStartFails(uri, "maxMemoryPages (1) is lower than the 2 pages");
    }

    @Test
    void maxOutputSizeLimitsStandardOutput() throws Exception {
        String module = module("flood1", FLOOD.formatted(1));
        try (CamelContext cc = start("wasm:" + module + "?maxOutputSize=1000")) {
            assertThat(send(cc, "").getMessage().getBody(byte[].class)).hasSize(1000);
        }
        try (CamelContext cc = start("wasm:" + module + "?maxOutputSize=999")) {
            Exchange out = send(cc, "in");

            assertThat(out.getException())
                    .isInstanceOf(CamelExchangeException.class)
                    .hasMessageContaining("wrote more than 999 bytes to its standard output (maxOutputSize)");
            assertThat(out.getMessage().getBody(String.class)).isEqualTo("in");
            assertThat(out.getMessage().getHeaders()).doesNotContainKey(Wasm.Headers.EXIT_CODE);
        }
    }

    @Test
    void maxOutputSizeLimitsStandardError() throws Exception {
        try (CamelContext cc = start("wasm:" + module("flood2", FLOOD.formatted(2)) + "?maxOutputSize=999")) {
            Exchange out = send(cc, "in");

            assertThat(out.getException())
                    .isInstanceOf(CamelExchangeException.class)
                    .hasMessageContaining("wrote more than 999 bytes to its standard error (maxOutputSize)");
            assertThat(out.getMessage().getHeader(Wasm.Headers.STDERR, String.class)).hasSize(900);
        }
    }

    @Test
    void everyExchangeRunsOnANewInstance() throws Exception {
        try (CamelContext cc = start("wasm:" + module("counter", COUNTER))) {
            for (int i = 0; i < 3; i++) {
                assertThat(text(send(cc, ""))).isEqualTo("1");
            }
        }
    }

    @Test
    void parallelExchanges() throws Exception {
        try (CamelContext cc = start("wasm:" + module("upper", UPPER))) {
            ProducerTemplate pt = cc.createProducerTemplate();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    final int thread = t;
                    results.add(pool.submit(() -> {
                        int ok = 0;
                        for (int i = 0; i < 50; i++) {
                            String in = ("thread " + thread + " message " + i + " ").repeat(1 + (i % 7));
                            byte[] out = pt.requestBody("direct:in", in, byte[].class);
                            if (new String(out, StandardCharsets.UTF_8).equals(in.toUpperCase(Locale.ROOT))) {
                                ok++;
                            }
                        }
                        return ok;
                    }));
                }
                for (Future<Integer> result : results) {
                    assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(50);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void moduleWithoutStartIsRejected() throws Exception {
        // the test module of the wasm language exports functions, not a program
        assertStartFails("wasm:classpath:functions.wasm", "has no _start function");
    }

    @Test
    void moduleWithOtherImportsIsRejected() throws Exception {
        String uri = "wasm:" + wat("other", "(module (import \"env\" \"f\" (func)) (func (export \"_start\")))");

        assertStartFails(uri, "cannot be provided: env.f");
    }

    /**
     * A body stream that records the threads that read it. When blocking, a read waits until the stream is released
     * (for 30 seconds at most) and, like a read from a socket, is not ended by an interrupt.
     */
    private static final class ObservedStream extends InputStream {
        final Set<Thread> readers = ConcurrentHashMap.newKeySet();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger inRead = new AtomicInteger();
        final CountDownLatch reading = new CountDownLatch(1);
        final CountDownLatch release;
        private final ByteArrayInputStream data;

        ObservedStream(String content, boolean blocking) {
            this.data = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            this.release = new CountDownLatch(blocking ? 1 : 0);
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            readers.add(Thread.currentThread());
            reads.incrementAndGet();
            inRead.incrementAndGet();
            try {
                reading.countDown();
                awaitRelease();
                return data.read(b, off, len);
            } finally {
                inRead.decrementAndGet();
            }
        }

        private void awaitRelease() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            boolean interrupted = false;
            while (true) {
                try {
                    // returns at once when the stream is released or the deadline has passed
                    release.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
