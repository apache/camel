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
package org.apache.camel.component.openfga;

import org.apache.camel.spi.Metadata;

/**
 * The headers the component sets.
 * <p/>
 * Every one of them is an <em>output</em>: the component writes them and never reads them back as an input. What is
 * asked of OpenFGA comes from the endpoint configuration alone, so a message cannot name the store, the authorization
 * model, the relation or the subject it would like to be judged as.
 */
public final class OpenFgaConstants {
    private static final String HEADER_PREFIX = "CamelOpenFga";

    @Metadata(label = "producer",
              description = "The allow/deny verdict of the authorization check. Always overwritten by the component,"
                            + " so a value set by an inbound message never survives into the route.",
              javaType = "Boolean")
    public static final String ALLOWED = HEADER_PREFIX + "Allowed";

    @Metadata(label = "producer",
              description = "Why the exchange was denied, set only on a deny. `denied` when OpenFGA evaluated the"
                            + " relationship and answered no; `missing-user`, `missing-object`, `missing-relation`,"
                            + " `wildcard-subject` or `invalid-identifier` when the exchange never reached OpenFGA"
                            + " because what it carried could not be used as a subject or an object.",
              javaType = "String")
    public static final String DENY_REASON = HEADER_PREFIX + "DenyReason";

    @Metadata(label = "producer",
              description = "Set to true only when the exchange proceeded because failOpen is enabled and OpenFGA"
                            + " could not be asked - nothing authorized it. Absent on every verdict OpenFGA actually"
                            + " gave, so a route or an audit trail can tell the two apart rather than seeing the same"
                            + " CamelOpenFgaAllowed=true for both.",
              javaType = "Boolean")
    public static final String FAILED_OPEN = HEADER_PREFIX + "FailedOpen";

    @Metadata(label = "producer",
              description = "The subject the check was made for, as resolved from the endpoint's user expression."
                            + " Set for observability; it is not read as an input.",
              javaType = "String")
    public static final String USER = HEADER_PREFIX + "User";

    @Metadata(label = "producer",
              description = "The object the check was made against, as resolved from the endpoint's object"
                            + " expression. Set for observability; it is not read as an input.",
              javaType = "String")
    public static final String OBJECT = HEADER_PREFIX + "Object";

    @Metadata(label = "producer",
              description = "The relation that was checked. Set for observability; it is not read as an input and"
                            + " cannot be used to demand a weaker permission than the endpoint configured.",
              javaType = "String")
    public static final String RELATION = HEADER_PREFIX + "Relation";

    @Metadata(label = "producer",
              description = "The identifier of the OpenFGA store that was consulted, so an audit trail records which"
                            + " relationship graph produced the verdict.",
              javaType = "String")
    public static final String STORE_ID = HEADER_PREFIX + "StoreId";

    @Metadata(label = "producer",
              description = "The continuation token the page came back with. Feed it back through the"
                            + " continuationToken option to read on. The two operations end differently: readTuples"
                            + " returns no token on its last page, so the header is absent once the read is done,"
                            + " whereas readChanges always returns a token - an empty body is what says the log has"
                            + " been read up to date, and that last token is what lets the next poll resume instead"
                            + " of replaying the whole log.",
              javaType = "String")
    public static final String CONTINUATION_TOKEN = HEADER_PREFIX + "ContinuationToken";

    @Metadata(label = "producer", description = "How many relationship tuples the writeTuples operation wrote.",
              javaType = "Integer")
    public static final String WRITTEN_TUPLES = HEADER_PREFIX + "WrittenTuples";

    @Metadata(label = "producer", description = "How many relationship tuples the deleteTuples operation deleted.",
              javaType = "Integer")
    public static final String DELETED_TUPLES = HEADER_PREFIX + "DeletedTuples";

    private OpenFgaConstants() {
    }
}
