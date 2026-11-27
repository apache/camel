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
package org.apache.camel.component.influxdb2;

import org.apache.camel.spi.Metadata;

public final class InfluxDb2Constants {
    @Metadata(description = "The key of the measurement name in a Map body, which the producer converts to a Point whose"
                            + " fields are the other entries of the Map. The producer does not read a message header of this"
                            + " name.",
              javaType = "String")
    public static final String MEASUREMENT = "CamelInfluxDB2MeasurementName";
    @Metadata(description = "The name of a retention policy, which InfluxDB 1.x has and InfluxDB 2.x does not.",
              javaType = "String",
              deprecationNote = "Never read by the component: the InfluxDB 2.x write API has no retention policy,"
                                + " retention is a property of the bucket")
    @Deprecated
    public static final String RETENTION_POLICY = "camelInfluxDB.RetentionPolicy";
    @Metadata(description = "The name of the organization to write to, overriding the org option on the endpoint.",
              javaType = "String")
    public static final String ORG = "CamelInfluxDB2Org";
    @Metadata(description = "The name of the bucket to write to, overriding the bucket option on the endpoint.",
              javaType = "String")
    public static final String BUCKET = "CamelInfluxDB2Bucket";
    @Metadata(description = "The precision of the timestamps in a Measurement, Record, Measurements or Records body, which"
                            + " replaces the writePrecision option. It does not apply to a Point, a Map or a Points body.",
              javaType = "com.influxdb.client.domain.WritePrecision")
    public static final String WRITE_PRECISION = "CamelInfluxDB2WritePrecision";

    private InfluxDb2Constants() {

    }

}
