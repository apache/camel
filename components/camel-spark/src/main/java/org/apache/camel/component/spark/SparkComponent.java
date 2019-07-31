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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.spark.api.java.JavaRDDLike;

@Component("spark")
public class SparkComponent extends DefaultComponent {

    private JavaRDDLike rdd;
    private RddCallback rddCallback;

    public SparkComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EndpointType type = getCamelContext().getTypeConverter().mandatoryConvertTo(EndpointType.class, remaining);
        return new SparkEndpoint(uri, this, type);
    }

    public JavaRDDLike getRdd() {
        return rdd;
    }

    /**
     * RDD to compute against.
     */
    public void setRdd(JavaRDDLike rdd) {
        this.rdd = rdd;
    }

    public RddCallback getRddCallback() {
        return rddCallback;
    }

    /**
     * Function performing action against an RDD.
     */
    public void setRddCallback(RddCallback rddCallback) {
        this.rddCallback = rddCallback;
    }

}
