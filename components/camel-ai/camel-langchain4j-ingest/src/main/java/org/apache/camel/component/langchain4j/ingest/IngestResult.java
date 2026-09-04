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
package org.apache.camel.component.langchain4j.ingest;

import java.util.Locale;

/**
 * Outcome of one ingestion, returned as the message body by the producer.
 */
public record IngestResult(String pipeline, String documentId, int segmentsWritten, Outcome outcome) {

    public enum Outcome {
        /** Segments written. */
        INGESTED,
        /** Blank document, nothing written. */
        EMPTY,
        /** Already ingested under the same key, nothing written. */
        SKIPPED;

        /** The stable wire/log form; not used in this component itself, but consumed by downstream runtimes. */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
