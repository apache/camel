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
package org.apache.camel.component.spark;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.spark.api.java.AbstractJavaRDDLike;
import org.apache.spark.sql.DataFrame;

public class SparkEndpoint extends DefaultEndpoint {

    // Endpoint collaborators

    private AbstractJavaRDDLike rdd;

    private RddCallback rddCallback;

    private DataFrame dataFrame;

    private DataFrameCallback dataFrameCallback;

    // Endpoint configuration

    private final EndpointType endpointType;

    private boolean collect = true;

    // Constructors

    public SparkEndpoint(String endpointUri, SparkComponent component, EndpointType endpointType) {
        super(endpointUri, component);
        this.endpointType = endpointType;
    }

    // Overridden

    @Override
    public Producer createProducer() throws Exception {
        if (endpointType == EndpointType.rdd) {
            return new RddSparkProducer(this);
        } else if (endpointType == EndpointType.dataframe) {
            return new DataFrameSparkProducer(this);
        } else {
            return new HiveSparkProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Spark component supports producer endpoints only.");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    // Setters & getters


    @Override
    public SparkComponent getComponent() {
        return (SparkComponent) super.getComponent();
    }

    public AbstractJavaRDDLike getRdd() {
        return rdd;
    }

    public void setRdd(AbstractJavaRDDLike rdd) {
        this.rdd = rdd;
    }

    public RddCallback getRddCallback() {
        return rddCallback;
    }

    public void setRddCallback(RddCallback rddCallback) {
        this.rddCallback = rddCallback;
    }

    public DataFrame getDataFrame() {
        return dataFrame;
    }

    public void setDataFrame(DataFrame dataFrame) {
        this.dataFrame = dataFrame;
    }

    public DataFrameCallback getDataFrameCallback() {
        return dataFrameCallback;
    }

    public void setDataFrameCallback(DataFrameCallback dataFrameCallback) {
        this.dataFrameCallback = dataFrameCallback;
    }

    public boolean isCollect() {
        return collect;
    }

    public void setCollect(boolean collect) {
        this.collect = collect;
    }

}