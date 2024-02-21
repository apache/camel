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

import org.apache.camel.spi.Metadata;

public final class FlinkConstants {

    @Metadata(description = "The dataset", javaType = "Object")
    public static final String FLINK_DATASET_HEADER = "CamelFlinkDataSet";
    @Metadata(description = "The dataset callback", javaType = "org.apache.camel.component.flink.DataSetCallback")
    public static final String FLINK_DATASET_CALLBACK_HEADER = "CamelFlinkDataSetCallback";
    @Metadata(description = "The data stream", javaType = "Object")
    public static final String FLINK_DATASTREAM_HEADER = "CamelFlinkDataStream";
    @Metadata(description = "The data stream callback", javaType = "org.apache.camel.component.flink.DataStreamCallback")
    public static final String FLINK_DATASTREAM_CALLBACK_HEADER = "CamelFlinkDataStreamCallback";

    private FlinkConstants() {
    }
}
