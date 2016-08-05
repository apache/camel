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
package org.apache.camel.component.flink;

import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.flink.streaming.api.datastream.DataStream;

public class DataStreamFlinkProducer extends DefaultProducer {

    public DataStreamFlinkProducer(FlinkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DataStream ds = resolveDataStream(exchange);
        DataStreamCallback dataStreamCallback = resolveDataStreamCallback(exchange);
        Object body = exchange.getIn().getBody();
        Object result = body instanceof List ? dataStreamCallback.onDataStream(ds, ((List) body).toArray(new Object[0])) : dataStreamCallback.onDataStream(ds, body);
        collectResults(exchange, result);
    }

    @Override
    public FlinkEndpoint getEndpoint() {
        return (FlinkEndpoint) super.getEndpoint();
    }

    protected void collectResults(Exchange exchange, Object result) {
        if (result instanceof DataStream) {
            DataStream dsResults = (DataStream) result;

            if (getEndpoint().isCollect()) {
                throw new IllegalArgumentException("collect mode not supported for Flink DataStreams.");
            } else {
                exchange.getIn().setBody(result);
                exchange.getIn().setHeader(FlinkConstants.FLINK_DATASTREAM_HEADER, result);
            }
        } else {
            exchange.getIn().setBody(result);
        }
    }

    protected DataStream resolveDataStream(Exchange exchange) {
        if (exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_HEADER) != null) {
            return (DataStream) exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_HEADER);
        } else if (getEndpoint().getDataStream() != null) {
            return getEndpoint().getDataStream();
        } else {
            throw new IllegalArgumentException("No DataStream defined");
        }
    }

    protected DataStreamCallback resolveDataStreamCallback(Exchange exchange) {
        if (exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER) != null) {
            return (DataStreamCallback) exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER);
        } else if (getEndpoint().getDataStreamCallback() != null) {
            return getEndpoint().getDataStreamCallback();
        } else {
            throw new IllegalArgumentException("Cannot resolve DataStream callback.");
        }
    }
}