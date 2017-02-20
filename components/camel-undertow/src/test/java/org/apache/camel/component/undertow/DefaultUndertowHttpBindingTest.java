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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class DefaultUndertowHttpBindingTest {

    @Test
    public void readEntireDelayedPayload() throws Exception {
        byte[] delayedPayload = "first ".getBytes();

        ExecutorService executor = Executors.newFixedThreadPool(1);
        StreamSourceChannel source = new EmptyStreamSourceChannel(
                new XnioIoThread(null, 0) {
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
                }) {
            int chunk = 0;

            @Override
            public int read(ByteBuffer dst) throws IOException {
                switch (chunk) {
                    case 0:
                        chunk++;
                        return 0;
                    case 1:
                        dst.put(delayedPayload);
                        chunk++;
                        return 6;
                }
                return -1;
            }
        };

        DefaultUndertowHttpBinding binding = new DefaultUndertowHttpBinding();
        byte[] result = binding.readFromChannel(source);

        assertThat(result, is(delayedPayload));
    }
}