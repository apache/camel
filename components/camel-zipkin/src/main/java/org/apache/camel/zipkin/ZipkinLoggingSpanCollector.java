/**
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
package org.apache.camel.zipkin;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To collect zipkin span's using a logger.
 */
public class ZipkinLoggingSpanCollector implements SpanCollector {

    private final String name;
    private final Logger logger;

    public ZipkinLoggingSpanCollector() {
        this(ZipkinLoggingSpanCollector.class.getName());
    }

    public ZipkinLoggingSpanCollector(String name) {
        this.name = name;
        this.logger = LoggerFactory.getLogger(name);
    }

    @Override
    public void collect(Span span) {
        if (logger.isTraceEnabled()) {
            String name = span.getName();
            String traceId = "" + span.getTrace_id();
            String spanId = "" + span.getId();
            String parentId = "" + span.getParent_id();
            long ms = span.getDuration() != null ? span.getDuration() / 1000 : -1;
            logger.info("Zipkin[name={}, traceId={}, spanId={}, parentId={}, duration={} ms]", name, traceId, spanId, parentId, ms);
        }
    }

    @Override
    public void addDefaultAnnotation(String key, String value) {
        // noop
    }

}
