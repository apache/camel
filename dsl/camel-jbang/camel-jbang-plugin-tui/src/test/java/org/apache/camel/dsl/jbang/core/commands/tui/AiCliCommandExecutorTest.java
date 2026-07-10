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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCliCommandExecutorTest {

    @Test
    void runRequestPreservesRawTail() {
        AiCliCommandExecutor.Request request = AiCliCommandExecutor.Request.run("route.yaml --dev");

        assertEquals(List.of("run", "route.yaml", "--dev"), request.argv());
        assertEquals("camel run route.yaml --dev", request.displayText());
    }

    @Test
    void infraRequestPreservesRawTail() {
        AiCliCommandExecutor.Request request = AiCliCommandExecutor.Request.infra("run kafka");

        assertEquals(List.of("infra", "run", "kafka"), request.argv());
        assertEquals("camel infra run kafka", request.displayText());
    }

    @Test
    void sendRequestMapsLiteralBodyToCmdSend() {
        AiCliCommandExecutor.Request request = AiCliCommandExecutor.Request.send("myApp",
                new AiSlashCommandRegistry.SendCommand("direct:foo", "hello world", false));

        assertEquals(List.of("cmd", "send", "myApp", "--endpoint=direct:foo", "--body=hello world"), request.argv());
    }

    @Test
    void sendRequestMapsFileBodyToCmdSendFileOption() {
        AiCliCommandExecutor.Request request = AiCliCommandExecutor.Request.send(null,
                new AiSlashCommandRegistry.SendCommand("direct:foo", "/tmp/payload.json", true));

        assertEquals(List.of("cmd", "send", "--endpoint=direct:foo", "--body=file:/tmp/payload.json"), request.argv());
    }

    @Test
    void runRequestHandlesQuotedValues() {
        assertEquals(List.of("run", "route.yaml", "--property=foo=hello world"),
                AiCliCommandExecutor.Request.run("route.yaml --property=\"foo=hello world\"").argv());
    }

    @Test
    void runRequestPreservesQuotesInsideQuotedValues() {
        assertEquals(List.of("run", "route.yaml", "--property=foo=He said 'hello'"),
                AiCliCommandExecutor.Request.run("route.yaml --property=\"foo=He said 'hello'\"").argv());
    }

    @Test
    void runRequestPreservesQuotesIntendedAsData() {
        assertEquals(List.of("run", "route.yaml", "--property=foo=\"hello\""),
                AiCliCommandExecutor.Request.run("route.yaml --property='foo=\"hello\"'").argv());
    }

    @Test
    void executorCapturesOutputAndExitCode() throws Exception {
        AiCliCommandExecutor executor = new AiCliCommandExecutor((argv, printer) -> {
            printer.println("created route");
            return 0;
        });

        AiCliCommandExecutor.Result result = executor.executeAsync(AiCliCommandExecutor.Request.run("route.yaml")).get();

        assertEquals(0, result.exitCode());
        assertEquals("created route\n", result.output());
        assertFalse(result.interrupted());
    }

    @Test
    void executorReportsNonZeroExit() throws Exception {
        AiCliCommandExecutor executor = new AiCliCommandExecutor((argv, printer) -> {
            printer.println("bad args");
            return 2;
        });

        AiCliCommandExecutor.Result result = executor.executeAsync(AiCliCommandExecutor.Request.infra("nope")).get();

        assertEquals(2, result.exitCode());
        assertTrue(result.output().contains("bad args"));
    }

    @Test
    void productionInvokerRestoresPrinterAndDoesNotQuitMain() throws Exception {
        CamelJBangMain main = new CamelJBangMain();
        Printer originalPrinter = main.getOut();
        CommandLine commandLine = new CommandLine(main)
                .addSubcommand("tui-test", new CommandLine(new TuiTestCommand(main)));
        Field field = CamelJBangMain.class.getDeclaredField("commandLine");
        field.setAccessible(true);
        CommandLine previous = (CommandLine) field.get(null);
        field.set(null, commandLine);
        try {
            AiCliCommandExecutor.Result result = new AiCliCommandExecutor()
                    .executeAsync(new AiCliCommandExecutor.Request(List.of("tui-test"), "camel tui-test"))
                    .get(5, TimeUnit.SECONDS);

            assertEquals(0, result.exitCode());
            assertEquals("captured command output\n", result.output());
            assertSame(originalPrinter, main.getOut());
        } finally {
            field.set(null, previous);
        }
    }

    @Test
    void cancelInterruptsRunningCommand() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AiCliCommandExecutor executor = new AiCliCommandExecutor((argv, printer) -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
            return 130;
        });

        CompletableFuture<AiCliCommandExecutor.Result> future = executor
                .executeAsync(AiCliCommandExecutor.Request.run("route.yaml"));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        executor.cancel();

        AiCliCommandExecutor.Result result = future.get(35, TimeUnit.SECONDS);
        assertTrue(result.interrupted());
    }

    @Test
    void cancelRemainsInterruptedWhenInvokerClearsInterruptStatus() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AiCliCommandExecutor executor = new AiCliCommandExecutor((argv, printer) -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                return 130;
            }
            return 0;
        });

        CompletableFuture<AiCliCommandExecutor.Result> future = executor
                .executeAsync(AiCliCommandExecutor.Request.run("route.yaml"));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        executor.cancel();

        assertTrue(future.get(5, TimeUnit.SECONDS).interrupted());
    }

    @Test
    void executeAsyncRejectsCommandAfterCancellationTimeoutUntilWorkerStops() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger invocations = new AtomicInteger();
        AiCliCommandExecutor executor = new AiCliCommandExecutor((argv, printer) -> {
            invocations.incrementAndGet();
            started.countDown();
            try {
                while (!stop.get()) {
                    Thread.onSpinWait();
                }
                return 0;
            } finally {
                stopped.countDown();
            }
        }, 10);

        CompletableFuture<AiCliCommandExecutor.Result> future = executor
                .executeAsync(AiCliCommandExecutor.Request.run("route.yaml"));
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            executor.cancel();

            AiCliCommandExecutor.Result result = future.get(5, TimeUnit.SECONDS);
            assertEquals(130, result.exitCode());
            assertTrue(result.interrupted());
            assertTrue(result.output().contains("Command cancellation timed out"));
            CompletableFuture<AiCliCommandExecutor.Result> rejected = executor
                    .executeAsync(AiCliCommandExecutor.Request.run("second-route.yaml"));
            assertThrows(ExecutionException.class, () -> rejected.get(5, TimeUnit.SECONDS));
            assertEquals(1, invocations.get());
        } finally {
            stop.set(true);
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
        }
    }

    @Command(name = "tui-test")
    private static final class TuiTestCommand implements Runnable {

        private final CamelJBangMain main;

        private TuiTestCommand(CamelJBangMain main) {
            this.main = main;
        }

        @Override
        public void run() {
            main.getOut().println("captured command output");
        }
    }
}
