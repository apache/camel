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
package org.apache.camel.component.mtconnect;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** A document an agent answers with: a probe, a streams, an assets or an error document. */
sealed interface AgentDocument permits AgentDocument.Probe, AgentDocument.Streams, AgentDocument.Assets, AgentDocument.Error {

    DocumentHeader header();

    /** An MTConnectDevices document: the data items by id, in document order. */
    record Probe(DocumentHeader header, Map<String, DataItem> dataItems) implements AgentDocument {
    }

    /** An MTConnectStreams document: its observations in sequence order; none in a heartbeat. */
    record Streams(DocumentHeader header, List<Observation> observations) implements AgentDocument {
    }

    /** An MTConnectAssets document: its assets in document order. */
    record Assets(DocumentHeader header, List<Asset> assets) implements AgentDocument {
    }

    /**
     * One asset of an MTConnectAssets document.
     *
     * @param type the local element name, such as CuttingTool or File
     * @param xml  the asset element as a standalone XML document, with the namespaces in scope declared on it
     */
    record Asset(String assetId, String type, Instant timestamp, String deviceUuid, boolean removed, String xml) {
    }

    /** An MTConnectError document, recognised by its root element whatever the HTTP status. */
    record Error(DocumentHeader header, List<AgentError> errors) implements AgentDocument {

        boolean hasCode(String code) {
            return errors.stream().anyMatch(error -> code.equals(error.code()));
        }

        /** The errors as one text: code: text; code: text. */
        String describe() {
            StringJoiner text = new StringJoiner("; ");
            for (AgentError error : errors) {
                text.add(error.code() + ": " + error.message());
            }
            return text.toString();
        }
    }

    /** One Error element: its errorCode attribute and its text. */
    record AgentError(String code, String message) {
    }
}
