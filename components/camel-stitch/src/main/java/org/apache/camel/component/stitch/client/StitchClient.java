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
package org.apache.camel.component.stitch.client;

import java.io.Closeable;

import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import reactor.core.publisher.Mono;

public interface StitchClient extends Closeable {
    /**
     * Create a batch
     *
     * Resource URL: /v2/import/batch
     *
     * Pushes a record or multiple records for a specified table to Stitch. Each request to this endpoint may only
     * contain data for a single table. When data for a table is pushed for the first time, Stitch will create the table
     * in the destination in the specified integration schema.
     *
     * How data is loaded during subsequent pushes depends on: 1. The loading behavior types used by the destination.
     * Stitch supports Upsert and Append-Only loading. 2. Whether the key_names property specifies Primary Key fields.
     * If Primary Keys aren’t specified, data will be loaded using Append-Only loading.
     *
     * @param requestBody the required arguments as StitchRequestBody
     */
    Mono<StitchResponse> batch(StitchRequestBody requestBody);
}
