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
package org.apache.camel.telemetrydev;

import java.io.StringWriter;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.telemetry.Op;

/*
 * An interface used to represent a trace in a given format.
 */
public interface DevTraceFormat {

    String format(DevTrace trace);

}

/*
 * Output regular Java toString().
 */
class DevTraceFormatDefault implements DevTraceFormat {

    @Override
    public String format(DevTrace trace) {
        return trace.toString();
    }

}

/*
 * Output basic Json representation.
 */
class DevTraceFormatJson implements DevTraceFormat {

    ObjectMapper mapper;

    DevTraceFormatJson() {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public String format(DevTrace trace) {
        try {
            return mapper.writeValueAsString(trace);
        } catch (JsonProcessingException e) {
            throw new RuntimeCamelException(e);
        }
    }
}

/*
 * Output a basic tree visual structure.
 */
class DevTraceFormatTree implements DevTraceFormat {

    @Override
    public String format(DevTrace trace) {
        StringWriter sw = new StringWriter();
        sw.append("\n| " + trace.getTraceId() + "\n");
        HashMap<String, Integer> depths = new HashMap<>();
        int depth = 0;
        for (int j = 0; j < trace.getSpans().size(); j++) {
            DevSpanAdapter span = trace.getSpans().get(j);
            String marker = getMarker(depths, span, j + 1 < trace.getSpans().size() ? trace.getSpans().get(j + 1) : null);
            String actualParentSpan = span.getTag("parentSpan");
            if (depths.containsKey(actualParentSpan)) {
                depth = depths.get(actualParentSpan);
            } else if (actualParentSpan != null) {
                depth++;
                depths.put(actualParentSpan, depth);
            } else {
                depth = 0;
            }

            if (depth > 0) {
                sw.append(" ");
                for (int i = 0; i < depth; i++) {
                    sw.append("   ");
                }
                if (depth == 0) {

                }
                if (depth > 1) {
                    for (int i = 1; i < depth; i++) {
                        sw.append(" ");
                    }
                }
            }
            sw.append(String.format("%s── %s\n", marker, formatSpan(span)));
        }
        return sw.toString();
    }

    private String formatSpan(DevSpanAdapter span) {
        if (span.getTag("isDone") == null || !span.getTag("isDone").equals("true")) {
            return String.format("[error: span %s not closed!]", span.getTag("component"));
        }
        if (span.getTag("initTimestamp") == null || span.getTag("endTimestamp") == null) {
            return String.format("[error: span %s incomplete!]", span.getTag("component"));
        }
        String sentOrReceived = null;
        if (span.getTag("op").equals(Op.EVENT_SENT.name())) {
            sentOrReceived = "-->";
        } else if (span.getTag("op").equals(Op.EVENT_RECEIVED.name())) {
            sentOrReceived = "<--";
        } else {
            sentOrReceived = "";
        }
        long nanos
                = (Long.parseLong(span.getTag("endTimestamp")) - Long.parseLong(span.getTag("initTimestamp"))) / (1000 * 1000);
        String component = span.getTag("component");
        String camelUri = span.getTag("camel.uri");
        return String.format(
                "| %s (%s) [%d millis] %s",
                camelUri == null ? "process" : camelUri,
                component,
                nanos,
                sentOrReceived);
    }

    private String getMarker(HashMap<String, Integer> depths, DevSpanAdapter span, DevSpanAdapter next) {
        if (next == null) {
            return "└";
        }
        Integer thisDepth = depths.get(span.getTag("parentSpan"));
        Integer nextDepth = depths.get(next.getTag("parentSpan"));
        if (thisDepth == null && nextDepth == null) {
            return "├";
        }
        if (thisDepth != null && nextDepth != null && nextDepth >= thisDepth) {
            return "├";
        }
        return "└";
    }

}
