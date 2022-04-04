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
package org.apache.camel.component.nitrite;

import org.apache.camel.spi.Metadata;

public final class NitriteConstants {
    @Metadata(label = "consumer", description = "Event timestamp in Epoch millis.", javaType = "long")
    public static final String CHANGE_TIMESTAMP = "CamelNitriteChangeTimestamp";
    @Metadata(label = "consumer", description = "Type of event.", javaType = "org.dizitart.no2.event.ChangeType")
    public static final String CHANGE_TYPE = "CamelNitriteChangeType";
    @Metadata(label = "producer",
              description = "Operation to invoke on Collection or Repository. Defaults to `UpsertOperation` if not specified.",
              javaType = "org.apache.camel.component.nitrite.AbstractNitriteOperation")
    public static final String OPERATION = "CamelNitriteOperation";
    @Metadata(description = "Result of data modifying operation.", javaType = "org.dizitart.no2.WriteResult")
    public static final String WRITE_RESULT = "CamelNitriteWriteResult";

    private NitriteConstants() {
    }
}
