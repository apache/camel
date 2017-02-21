package org.apache.camel.component.undertow;

import org.junit.Test;
import org.xnio.XnioIoThread;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class DefaultUndertowHttpBindingTest {

    @Test(timeout = 1000)
    public void readEntireDelayedPayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "",
                "chunk",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        checkResult(result, delayedPayloads);
    }

    @Test(timeout = 1000)
    public void readEntireMultiDelayedPayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "",
                "first ",
                "second",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        checkResult(result, delayedPayloads);
    }

    private void checkResult(String result, String[] delayedPayloads) {
        assertThat(result, is(
                Stream.of(delayedPayloads)
                        .collect(Collectors.joining())));
    }

    @Test(timeout = 1000)
    public void readEntireMultiDelayedWithPausePayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "",
                "first ",
                "",
                "second",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        checkResult(result, delayedPayloads);
    }

    private StreamSourceChannel source(final String[] delayedPayloads) {
        Thread sourceThread = Thread.currentThread();

        return new EmptyStreamSourceChannel(thread()) {
            int chunk = 0;
            boolean mustWait = false;  // make sure that the caller is not spinning on read==0

            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (mustWait) {
                    fail("must wait before reading");
                }
                if (chunk < delayedPayloads.length) {
                    byte[] delayedPayload = delayedPayloads[chunk].getBytes();
                    dst.put(delayedPayload);
                    chunk++;
                    if (delayedPayload.length == 0) {
                        mustWait = true;
                    }
                    return delayedPayload.length;
                }
                return -1;
            }

            @Override
            public void resumeReads() {
                /**
                 * {@link io.undertow.server.HttpServerExchange.ReadDispatchChannel} delays resumes in the main thread
                 */
                if (sourceThread != Thread.currentThread()) {
                    super.resumeReads();
                }
            }

            @Override
            public void awaitReadable() throws IOException {
                mustWait = false;
                super.awaitReadable();
            }
        };
    }

    private XnioIoThread thread() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        return new XnioIoThread(null, 0) {
            @Override
            public void execute(Runnable runnable) {
                executor.execute(runnable);
            }

            @Override
            public Key executeAfter(Runnable runnable, long l, TimeUnit timeUnit) {
                execute(runnable);
                return null;
            }

            @Override
            public Key executeAtInterval(Runnable runnable, long l, TimeUnit timeUnit) {
                execute(runnable);
                return null;
            }
        };
    }
}