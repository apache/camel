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
package org.apache.camel.impl.engine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.MessageSizeStrategy;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default implementation of {@link MessageSizeStrategy} that computes message payload sizes for known body types.
 */
public class DefaultMessageSizeStrategy extends ServiceSupport implements CamelContextAware, MessageSizeStrategy {

    private CamelContext camelContext;
    private boolean enabled;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public long computeBodySize(Message message) {
        Object body = message.getBody();
        if (body == null) {
            return 0;
        }
        if (body instanceof byte[] bytes) {
            return bytes.length;
        }
        if (body instanceof String str) {
            return str.getBytes(StandardCharsets.UTF_8).length;
        }
        if (body instanceof StreamCache sc) {
            long len = sc.length();
            return len > 0 ? len : -1;
        }
        if (body instanceof WrappedFile<?> wf) {
            long len = wf.getFileLength();
            return len > 0 ? len : -1;
        }
        if (body instanceof File f) {
            return f.length();
        }
        if (body instanceof Path p) {
            try {
                return Files.size(p);
            } catch (Exception e) {
                return -1;
            }
        }
        // fallback to Content-Length header (e.g. HTTP components where body is a stream)
        Long cl = message.getHeader(Exchange.CONTENT_LENGTH, Long.class);
        if (cl != null && cl >= 0) {
            return cl;
        }
        return -1;
    }

    @Override
    public long computeHeadersSize(Message message) {
        Map<String, Object> headers = message.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key != null) {
                total += key.getBytes(StandardCharsets.UTF_8).length;
            }
            Object value = entry.getValue();
            if (value != null) {
                String s = value.toString();
                total += s.getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return total;
    }
}
