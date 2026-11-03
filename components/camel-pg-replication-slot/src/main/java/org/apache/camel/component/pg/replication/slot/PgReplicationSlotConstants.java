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
package org.apache.camel.component.pg.replication.slot;

import org.apache.camel.spi.Metadata;

/**
 * The headers of the pg-replication-slot component.
 */
public final class PgReplicationSlotConstants {

    @Metadata(label = "consumer",
              description = "The log sequence number (LSN) of the message, in the text form of PostgreSQL such as 0/16B3748."
                            + " Several messages can have the same LSN, for example the BEGIN and the first change of a"
                            + " transaction.",
              javaType = "String")
    public static final String LSN = "CamelPgReplicationSlotLsn";

    private PgReplicationSlotConstants() {
    }
}
