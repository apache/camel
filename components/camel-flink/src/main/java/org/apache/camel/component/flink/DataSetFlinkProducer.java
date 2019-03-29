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
package org.apache.camel.component.flink;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.flink.api.java.DataSet;

public class DataSetFlinkProducer extends DefaultProducer {

    public DataSetFlinkProducer(FlinkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DataSet ds = resolveDataSet(exchange);
        DataSetCallback dataSetCallback = resolveDataSetCallback(exchange);
        Object body = exchange.getIn().getBody();

        Object result;
        
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(DataSet.class.getClassLoader());
            if (body instanceof List) {
                List list = (List) body;
                Object[] array = list.toArray(new Object[list.size()]);
                result = dataSetCallback.onDataSet(ds, array);
            } else {
                result = dataSetCallback.onDataSet(ds, body);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
        
        collectResults(exchange, result);
    }

    @Override
    public FlinkEndpoint getEndpoint() {
        return (FlinkEndpoint) super.getEndpoint();
    }

    protected void collectResults(Exchange exchange, Object result) throws Exception {
        if (result instanceof DataSet) {
            DataSet dsResults = (DataSet) result;
            if (getEndpoint().isCollect()) {
                exchange.getIn().setBody(dsResults.collect());
            } else {
                exchange.getIn().setBody(result);
                exchange.getIn().setHeader(FlinkConstants.FLINK_DATASET_HEADER, result);
            }
        } else {
            exchange.getIn().setBody(result);
        }
    }

    protected DataSet resolveDataSet(Exchange exchange) {
        if (exchange.getIn().getHeader(FlinkConstants.FLINK_DATASET_HEADER) != null) {
            return (DataSet) exchange.getIn().getHeader(FlinkConstants.FLINK_DATASET_HEADER);
        } else if (getEndpoint().getDataSet() != null) {
            return getEndpoint().getDataSet();
        } else {
            throw new IllegalStateException("No DataSet defined");
        }
    }

    protected DataSetCallback resolveDataSetCallback(Exchange exchange) {
        if (exchange.getIn().getHeader(FlinkConstants.FLINK_DATASET_CALLBACK_HEADER) != null) {
            return (DataSetCallback) exchange.getIn().getHeader(FlinkConstants.FLINK_DATASET_CALLBACK_HEADER);
        } else if (getEndpoint().getDataSetCallback() != null) {
            return getEndpoint().getDataSetCallback();
        } else {
            throw new IllegalStateException("Cannot resolve DataSet callback.");
        }
    }
}