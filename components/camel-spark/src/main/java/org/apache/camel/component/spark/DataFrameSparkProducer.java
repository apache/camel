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
package org.apache.camel.component.spark;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.camel.component.spark.SparkConstants.SPARK_DATAFRAME_CALLBACK_HEADER;
import static org.apache.camel.component.spark.SparkConstants.SPARK_DATAFRAME_HEADER;
import static org.apache.camel.component.spark.SparkConstants.SPARK_RDD_HEADER;

public class DataFrameSparkProducer extends DefaultProducer {

    public DataFrameSparkProducer(SparkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Dataset<Row> dataFrame = resolveDataFrame(exchange);
        DataFrameCallback dataFrameCallback = resolveDataFrameCallback(exchange);
        Object body = exchange.getIn().getBody();
        Object result = body instanceof List ? dataFrameCallback.onDataFrame(dataFrame, ((List) body).toArray(new Object[0])) : dataFrameCallback.onDataFrame(dataFrame, body);
        collectResults(exchange, result);
    }

    @Override
    public SparkEndpoint getEndpoint() {
        return (SparkEndpoint) super.getEndpoint();
    }

    // Helpers

    protected void collectResults(Exchange exchange, Object result) {
        if (result instanceof JavaRDD) {
            JavaRDD rddResults = (JavaRDD) result;
            if (getEndpoint().isCollect()) {
                exchange.getIn().setBody(rddResults.collect());
            } else {
                exchange.getIn().setBody(result);
                exchange.getIn().setHeader(SPARK_RDD_HEADER, result);
            }
        } else {
            exchange.getIn().setBody(result);
        }
    }

    protected Dataset<Row> resolveDataFrame(Exchange exchange) {
        if (exchange.getIn().getHeader(SPARK_DATAFRAME_HEADER) != null) {
            return (Dataset<Row>) exchange.getIn().getHeader(SPARK_DATAFRAME_HEADER);
        } else if (getEndpoint().getDataFrame() != null) {
            return getEndpoint().getDataFrame();
        } else {
            throw new IllegalStateException("No Data Frame defined.");
        }
    }

    protected DataFrameCallback resolveDataFrameCallback(Exchange exchange) {
        if (exchange.getIn().getHeader(SPARK_DATAFRAME_CALLBACK_HEADER) != null) {
            return  (DataFrameCallback) exchange.getIn().getHeader(SPARK_DATAFRAME_CALLBACK_HEADER);
        } else if (getEndpoint().getDataFrameCallback() != null) {
            return getEndpoint().getDataFrameCallback();
        } else {
            throw new IllegalStateException("Cannot resolve Data Frame.");
        }
    }

}
