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
package org.apache.camel.component.stitch;

import org.apache.camel.spi.Metadata;

public final class StitchConstants {
    private static final String HEADER_PREFIX = "CamelStitch";
    // headers evaluated by producer
    @Metadata(label = "producer", description = "The name of the destination table that the records are loaded into. It"
                                                + " overrides the table name of the endpoint URI.",
              javaType = "String")
    public static final String TABLE_NAME = HEADER_PREFIX + "TableName";
    @Metadata(label = "producer",
              description = "The JSON schema that describes the records, as a StitchSchema or as a Map of JSON schema"
                            + " keywords. It overrides the stitchSchema option; a value of another type is ignored.",
              javaType = "org.apache.camel.component.stitch.client.models.StitchSchema or Map")
    public static final String SCHEMA = HEADER_PREFIX + "Schema";
    @Metadata(label = "producer",
              description = "The Primary Key fields of the source table, as a comma separated String without spaces. It"
                            + " overrides the keyNames option.",
              javaType = "Collection<String>")
    public static final String KEY_NAMES = HEADER_PREFIX + "KeyNames";
    // headers set by producer
    @Metadata(label = "producer",
              description = "The HTTP status code of the response of the Stitch Import API. Set only when the request"
                            + " succeeds: a status code of 300 or more fails the exchange with a StitchException.",
              javaType = "Integer")
    public static final String CODE = HEADER_PREFIX + "Code";
    @Metadata(label = "producer", description = "The HTTP headers of the response of the Stitch Import API, by header name."
                                                + " Set only when the request succeeds.",
              javaType = "Map<String, Object>")
    public static final String HEADERS = HEADER_PREFIX + "Headers";
    @Metadata(label = "producer",
              description = "The status field of the JSON response of the Stitch Import API, empty when the response has"
                            + " none. Set only when the request succeeds.",
              javaType = "String")
    public static final String STATUS = HEADER_PREFIX + "Status";

    private StitchConstants() {
    }
}
