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

public class DefaultUndertowHttpBindingTest {

    @Test(timeout = 1000)
    public void readEntireDelayedPayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "chunk",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        assertThat(result, is(delayedPayloads[0]));
    }

    @Test(timeout = 1000)
    public void readEntireMultiDelayedPayload() throws Exception {
        String[] delayedPayloads = new String[] {
                "first ",
                "second",
        };

        StreamSourceChannel source = source(delayedPayloads);

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        String result = new String(binding.readFromChannel(source));

        assertThat(result, is(
                Stream.of(delayedPayloads)
                        .collect(Collectors.joining())));
    }

    private StreamSourceChannel source(final String[] delayedPayloads) {
        XnioIoThread thread = thread();
        Thread sourceThread = Thread.currentThread();

        return new EmptyStreamSourceChannel(thread) {
            int chunk = 0;

            @Override
            public int read(ByteBuffer dst) throws IOException {
                // can only read payloads in the reader thread
                if (sourceThread != Thread.currentThread()) {
                    if (chunk < delayedPayloads.length) {
                        byte[] delayedPayload = delayedPayloads[chunk].getBytes();
                        dst.put(delayedPayload);
                        chunk++;
                        return delayedPayload.length;
                    }
                    return -1;
                }
                return 0;
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