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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/** Evaluates one selector and dispatches through a table of literal keys. */
public class SwitchProcessor extends BaseProcessorSupport
        implements Navigate<Processor>, Traceable, IdAware, RouteIdAware, StepIdAware {
    private static final Logger LOG = LoggerFactory.getLogger(SwitchProcessor.class);
    private final CamelContext context;
    private final Expression selector;
    private final Map<String, Integer> table = new LinkedHashMap<>();
    private final List<AsyncProcessor> processors = new ArrayList<>();
    private final AsyncProcessor otherwise;
    private final AtomicLongArray counts;
    private String id;
    private String routeId;
    private String stepId;

    public SwitchProcessor(CamelContext context, Expression selector,
                           Map<String, Processor> cases, Processor otherwise) {
        this.context = context;
        this.selector = selector;
        cases.forEach((key, processor) -> {
            table.put(key, processors.size());
            processors.add(AsyncProcessorConverterHelper.convert(processor));
        });
        this.otherwise = otherwise == null ? null : AsyncProcessorConverterHelper.convert(otherwise);
        counts = new AtomicLongArray(processors.size() + 1);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String key = null;
        try {
            Object result = selector.evaluate(exchange, Object.class);
            if (result != null) {
                if (result instanceof Map<?, ?>) {
                    throw new IllegalArgumentException(
                            "Switch requires a scalar selector result, but received a Map. Composite selector results are not supported.");
                }
                if (result instanceof Iterable<?> || result.getClass().isArray()) {
                    throw new IllegalArgumentException(
                            "Switch requires a scalar selector result, but received a collection or array");
                }
                String text = context.getTypeConverter().mandatoryConvertTo(String.class, exchange, result);
                key = text.toLowerCase(Locale.ENGLISH);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            MessageHelper.resetStreamCache(exchange.getIn());
        }
        if (!continueProcessing(exchange, "so breaking out of switch", LOG)) {
            callback.done(true);
            return true;
        }
        Integer index = key == null ? null : table.get(key);
        if (index != null) {
            counts.incrementAndGet(index);
            return processors.get(index).process(exchange, callback);
        }
        counts.incrementAndGet(processors.size());
        if (otherwise != null) {
            return otherwise.process(exchange, callback);
        }
        callback.done(true);
        return true;
    }

    public long getMatchedCount(int index) {
        return counts.get(index);
    }

    public long getUnmatchedCount() {
        return counts.get(processors.size());
    }

    public void reset() {
        for (int i = 0; i < counts.length(); i++) {
            counts.set(i, 0);
        }
    }

    @Override
    public List<Processor> next() {
        List<Processor> answer = new ArrayList<>(processors);
        if (otherwise != null) {
            answer.add(otherwise);
        }
        return answer;
    }

    @Override
    public boolean hasNext() {
        return !processors.isEmpty() || otherwise != null;
    }

    @Override
    public String getTraceLabel() {
        return "switch";
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(processors, otherwise);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processors, otherwise);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(otherwise, processors);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(otherwise, processors);
    }
}
