package org.apache.camel.component.jms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.CachedOutputStream;

public class SplitCache {
    private OutputStream cache;
    private boolean streamCacheEnabled;

    public SplitCache(Exchange exchange) throws IOException {
        streamCacheEnabled = exchange.getContext().getStreamCachingStrategy().isEnabled();
        if (streamCacheEnabled) {
            cache = new CachedOutputStream(exchange);
        } else {
            cache = new ByteArrayOutputStream();
        }
        cache.flush();
    }

    public void add(byte[] piece) throws IOException {
        cache.write(piece);
        cache.flush();
    }

    public OutputStream getCache() {
        return cache;
    }

    public boolean isStreamCacheEnabled() {
        return streamCacheEnabled;
    }
}
