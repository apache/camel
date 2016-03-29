/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.zipkin;

import java.util.LinkedHashSet;
import java.util.Set;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To collect zipkin span's using a logger.
 */
public class ZipkinLoggingSpanCollector implements SpanCollector {

    private final Set<BinaryAnnotation> defaultAnnotations = new LinkedHashSet<BinaryAnnotation>();
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
        if (!defaultAnnotations.isEmpty()) {
            for (BinaryAnnotation ba : defaultAnnotations) {
                span.addToBinary_annotations(ba);
            }
        }

        if (logger.isInfoEnabled()) {
            long ms = span.getDuration() != null ? span.getDuration() / 1000 : -1;
            String id = IdConversion.convertToString(span.getId());
            String line = String.format("%s(%s) - %s ms", span.getName(), id, ms);
            logger.info(line);
        }
    }

    @Override
    public void addDefaultAnnotation(String key, String value) {
        defaultAnnotations.add(BinaryAnnotation.create(key, value, null));
    }

}
