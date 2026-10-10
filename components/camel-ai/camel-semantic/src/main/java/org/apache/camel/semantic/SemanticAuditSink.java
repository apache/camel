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
package org.apache.camel.semantic;

import org.apache.camel.Service;

/**
 * Pluggable destination for safe records. Calls are serialized per audit dispatcher. Returning means the destination
 * accepted the record, not that it is durably committed. A destination must document its own durability, bound its I/O
 * and honor interruption. The dispatcher does not retry failed appends; backend retries must preserve the event ID.
 */
public interface SemanticAuditSink extends Service {
    void append(SemanticAuditRecord record) throws Exception;
}
