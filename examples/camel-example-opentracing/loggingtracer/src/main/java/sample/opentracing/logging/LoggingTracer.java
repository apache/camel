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
package sample.opentracing.logging;

import java.util.List;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

public class LoggingTracer extends MockTracer {

    public LoggingTracer() {
        super(MockTracer.Propagator.TEXT_MAP);
    }

    @Override
    protected void onSpanFinished(MockSpan mockSpan) {
        System.out.println("SPAN FINISHED: traceId=" + mockSpan.context().traceId()
                + " spanId=" + mockSpan.context().spanId()
                + " parentId=" + mockSpan.parentId()
                + " operation=" + mockSpan.operationName()
                + " tags=" + mockSpan.tags()
                + " logs=[" + toText(mockSpan.logEntries())
                + "]");
    }

    protected String toText(List<MockSpan.LogEntry> logEntries) {
        StringBuilder builder = new StringBuilder();
        logEntries.forEach(entry -> builder.append(entry.fields()));
        return builder.toString();
    }
}