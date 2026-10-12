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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.wasm.WasiCommand;
import org.apache.camel.wasm.Wasm;
import run.endive.runtime.TrapException;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/**
 * Runs a WASI Preview 1 command module once per exchange: the message body is the standard input of the module and its
 * standard output becomes the message body.
 * <p/>
 * The module is parsed once, when the producer is initialized, and every exchange runs on a new instance of it, so
 * exchanges never share guest memory or globals and need no lock. The instance is dropped after its run, whatever the
 * outcome.
 * <p/>
 * Without a timeout the module runs on the calling thread and reads the body as a stream. With a timeout the module
 * runs on a thread of the producer, and the body is first read into memory on the calling thread: the module then reads
 * a private copy, so a module that is abandoned after a timeout never reads a stream that the route is still using.
 * <p/>
 * When the exchange fails, the producer does not set a new body, but the module may have read the old one: a body that
 * is a stream has been read, partly or to its end. With stream caching (the default) the body can be read again: Camel
 * resets a {@link StreamCache}. When a timeout is set, a body that was a stream but not a StreamCache is replaced by
 * the bytes that were read from it, so that error handling still sees the content.
 */
public class WasmProducer extends DefaultProducer {

    private static final int MAX_STDERR_IN_MESSAGE = 256;
    private static final String DEFAULT_PROGRAM_NAME = "module.wasm";

    private WasiCommand command;
    private Map<String, String> environment = Collections.emptyMap();
    private List<String> environmentHeaders = Collections.emptyList();
    private volatile ExecutorService executor;

    public WasmProducer(WasmEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WasmEndpoint getEndpoint() {
        return (WasmEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        WasmEndpoint endpoint = getEndpoint();
        WasmConfiguration conf = endpoint.getConfiguration();

        Map<String, String> env = new LinkedHashMap<>();
        if (conf.getEnvironment() != null) {
            for (Map.Entry<String, Object> e : conf.getEnvironment().entrySet()) {
                String value = e.getValue() != null ? e.getValue().toString() : "";
                env.put(checkName(e.getKey()), checkNoNul("environment variable " + e.getKey(), value));
            }
        }
        List<String> headers = new ArrayList<>();
        if (ObjectHelper.isNotEmpty(conf.getEnvironmentHeaders())) {
            for (String header : conf.getEnvironmentHeaders().split(",")) {
                String name = checkName(header.trim());
                if (env.containsKey(name)) {
                    throw new IllegalArgumentException(
                            "Environment variable " + name + " is configured both in environment and environmentHeaders");
                }
                headers.add(name);
            }
        }
        List<String> arguments = arguments(programName(endpoint.getModule()), conf.getArgs());
        for (String argument : arguments) {
            checkNoNul("argument " + argument, argument);
        }

        WasmModule module;
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(
                endpoint.getCamelContext(), endpoint.getModule())) {
            module = Parser.parse(is);
        }

        this.environment = Collections.unmodifiableMap(env);
        this.environmentHeaders = List.copyOf(headers);
        this.command = new WasiCommand(module, arguments, conf.getMaxMemoryPages());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getEndpoint().getConfiguration().getTimeout() > 0 && executor == null) {
            executor = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newCachedThreadPool(this, "Wasm");
        }
    }

    @Override
    protected void doStop() throws Exception {
        ExecutorService pool = executor;
        executor = null;
        if (pool != null) {
            // interrupts the modules that are still running
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(pool);
        }

        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WasmConfiguration conf = getEndpoint().getConfiguration();
        Message message = exchange.getMessage();

        // the result headers only ever describe this run: never a previous one, nor a value sent with the message
        message.removeHeader(Wasm.Headers.EXIT_CODE);
        message.removeHeader(Wasm.Headers.STDERR);

        Map<String, String> env = environment(message);
        // no body is empty standard input; a body that cannot be read as a stream fails the exchange instead of
        // silently running the module with empty input
        Object body = message.getBody();
        InputStream in = body != null ? message.getMandatoryBody(InputStream.class) : InputStream.nullInputStream();
        byte[] restore = null;
        if (conf.getTimeout() > 0 && body != null) {
            // with a timeout the module runs on another thread, which goes on for a moment after the timeout and is
            // not stopped by an interrupt while it is blocked in a read of the body. So the body is read here, on the
            // calling thread, and the module gets a copy that no other thread uses and that never blocks.
            byte[] input = in.readAllBytes();
            in = new ByteArrayInputStream(input);
            if (body instanceof InputStream && !(body instanceof StreamCache)) {
                // the stream of the body is used up: if the exchange fails, the body becomes what was read from it
                restore = input;
            }
        }
        InputStream stdin = in;
        LimitedOutputStream stdout = new LimitedOutputStream("standard output", conf.getMaxOutputSize());
        LimitedOutputStream stderr = new LimitedOutputStream("standard error", conf.getMaxOutputSize());

        boolean done = false;
        try {
            int exitCode;
            try {
                exitCode = run(exchange, conf.getTimeout(), () -> command.run(stdin, stdout, stderr, env));
            } catch (TrapException e) {
                String err = setStderr(message, stderr);
                throw new CamelExchangeException(
                        "The Wasm module " + name() + " trapped: " + e.getMessage() + excerpt(err),
                        exchange, e);
            } catch (OutputLimitExceededException e) {
                setStderr(message, stderr);
                throw new CamelExchangeException(
                        "The Wasm module " + name() + " was stopped: " + e.getMessage(), exchange);
            }

            message.setHeader(Wasm.Headers.EXIT_CODE, exitCode);
            String err = setStderr(message, stderr);
            if (exitCode != 0 && conf.isFailOnNonZeroExit()) {
                throw new WasmExitCodeException(
                        "The Wasm module " + name() + " exited with code " + exitCode + excerpt(err),
                        exitCode, err, exchange);
            }
            message.setBody(stdout.toByteArray());
            done = true;
        } finally {
            if (!done && restore != null) {
                message.setBody(restore);
            }
        }
    }

    /**
     * Runs the module on the calling thread, or on a thread of the producer when a timeout is set. On timeout that
     * thread is interrupted: Endive checks the interrupt flag on every call and every branch, and its WASI sleep
     * (poll_oneoff) ends on an interrupt, so a module stops at its next call or loop iteration. Interrupting a thread
     * of the producer, not the calling thread, keeps the interrupt flag away from the threads of the route.
     * <p/>
     * The threads come from a cached pool without a maximum size. It needs none: a thread is only taken while an
     * exchange waits for it, so there are as many threads as exchanges in this producer at the same time, plus the
     * threads of timed out runs for the moment they need to reach their next call or branch. The standard streams of a
     * run with a timeout are all in memory, so no call into the host can keep a thread after its exchange is gone.
     */
    private int run(Exchange exchange, long timeout, Callable<Integer> task) throws Exception {
        if (timeout <= 0) {
            return task.call();
        }
        ExecutorService pool = executor;
        if (pool == null) {
            throw new RejectedExecutionException("The wasm producer is not started");
        }
        Future<Integer> future = pool.submit(task);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ExchangeTimedOutException(
                    exchange, timeout, "the Wasm module " + name() + " did not finish in time and was interrupted");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    private String name() {
        return URISupport.sanitizeUri(getEndpoint().getModule());
    }

    private Map<String, String> environment(Message message) {
        if (environmentHeaders.isEmpty()) {
            return environment;
        }
        Map<String, String> answer = new LinkedHashMap<>(environment);
        for (String name : environmentHeaders) {
            String value = message.getHeader(name, String.class);
            if (value != null) {
                answer.put(name, checkNoNul("header " + name, value));
            }
        }
        return answer;
    }

    private static String setStderr(Message message, LimitedOutputStream stderr) {
        String answer = stderr.toString();
        if (!answer.isEmpty()) {
            message.setHeader(Wasm.Headers.STDERR, answer);
        }
        return answer;
    }

    private static String excerpt(String stderr) {
        String s = stderr.strip();
        if (s.isEmpty()) {
            return "";
        }
        return ": " + (s.length() > MAX_STDERR_IN_MESSAGE ? s.substring(0, MAX_STDERR_IN_MESSAGE) + "..." : s);
    }

    private static String checkName(String name) {
        if (ObjectHelper.isEmpty(name) || name.indexOf('=') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid environment variable name: '" + name + "'");
        }
        return name;
    }

    private static String checkNoNul(String what, String value) {
        // WASI passes arguments and environment variables as NUL terminated strings
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The " + what + " contains a NUL character, which a module cannot receive");
        }
        return value;
    }

    /**
     * The program name the module receives as argument zero: the file name of the module location, without scheme,
     * directories, user info or query. The location itself is not passed on: it can carry credentials (an http location
     * with user info or a token in the query) and shows host paths to a module that has no file access.
     */
    static String programName(String location) {
        String name = location;
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int scheme = name.indexOf(':');
        if (scheme > 0) {
            String prefix = name.substring(0, scheme);
            if ("base64".equals(prefix) || "gzip".equals(prefix)) {
                // the rest is the module itself, not a name
                return DEFAULT_PROGRAM_NAME;
            }
            name = name.substring(scheme + 1);
        }
        if (name.startsWith("//")) {
            // host, port and user info are not part of the name
            int path = name.indexOf('/', 2);
            name = path < 0 ? "" : name.substring(path);
        }
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.isEmpty() ? DEFAULT_PROGRAM_NAME : name;
    }

    /**
     * Splits the args option into the argument list of the module, with the program name first. Arguments are separated
     * by whitespace; double quotes group an argument that contains whitespace.
     */
    static List<String> arguments(String program, String args) {
        List<String> answer = new ArrayList<>();
        answer.add(program);
        if (args == null) {
            return answer;
        }
        StringBuilder current = null;
        boolean quoted = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                if (current == null) {
                    current = new StringBuilder();
                }
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current != null) {
                    answer.add(current.toString());
                    current = null;
                }
            } else {
                if (current == null) {
                    current = new StringBuilder();
                }
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unbalanced double quote in args: " + args);
        }
        if (current != null) {
            answer.add(current.toString());
        }
        return answer;
    }

    /**
     * Thrown by a {@link LimitedOutputStream} that is full. It is not an IOException, so that it stops the module
     * instead of being returned to the module as a failed write.
     */
    private static final class OutputLimitExceededException extends RuntimeException {
        OutputLimitExceededException(String message) {
            super(message, null, false, false);
        }
    }

    /**
     * Buffers what the module writes to one of its output streams, up to maxOutputSize bytes (0 means no limit).
     */
    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final String name;
        private final int max;

        LimitedOutputStream(String name, int max) {
            this.name = name;
            this.max = max;
        }

        @Override
        public void write(int b) {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (max > 0 && len > max - buffer.size()) {
                throw new OutputLimitExceededException(
                        "it wrote more than " + max + " bytes to its " + name + " (maxOutputSize)");
            }
            buffer.write(b, off, len);
        }

        byte[] toByteArray() {
            return buffer.toByteArray();
        }

        @Override
        public String toString() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
