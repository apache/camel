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
package org.apache.camel.component.hbase;

import org.apache.camel.spi.Metadata;

public interface HBaseConstants {

    @Metadata(label = "producer", description = "The HBase operation to perform", javaType = "String")
    String OPERATION = "CamelHBaseOperation";

    String PUT = "CamelHBasePut";
    String GET = "CamelHBaseGet";
    String SCAN = "CamelHBaseScan";
    String DELETE = "CamelHBaseDelete";

    @Metadata(label = "producer", description = "The maximum number of rows to scan.", javaType = "Integer")
    String HBASE_MAX_SCAN_RESULTS = "CamelHBaseMaxScanResults";
    @Metadata(label = "producer", description = "The row to start scanner at or after", javaType = "String")
    String FROM_ROW = "CamelHBaseStartRow";
    @Metadata(label = "producer", description = "The row to end at (exclusive)", javaType = "String")
    String STOP_ROW = "CamelHBaseStopRow";
    @Metadata(description = "The strategy to use for mapping Camel messages to HBase columns.\n\nSupported values:\n\n* header\n* body",
              javaType = "String")
    String STRATEGY = "CamelMappingStrategy";
    @Metadata(description = "The class name of a custom mapping strategy implementation.", javaType = "String")
    String STRATEGY_CLASS_NAME = "CamelMappingStrategyClassName";
    @Metadata(label = "consumer", description = "The marked row id", javaType = "byte[]")
    String HBASE_MARKED_ROW_ID = HBaseAttribute.HBASE_MARKED_ROW_ID.asHeader();
}
