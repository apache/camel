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
package org.apache.camel.component.couchdb;

import org.apache.camel.spi.Metadata;

/**
 *
 */
public interface CouchDbConstants {

    @Metadata(label = "consumer", description = "The database the message came from", javaType = "String")
    String HEADER_DATABASE = "CouchDbDatabase";
    @Metadata(label = "consumer", description = "The couchdb changeset sequence number of the update / delete message",
              javaType = "String")
    String HEADER_SEQ = "CouchDbSeq";
    @Metadata(description = "The couchdb document id", javaType = "String")
    String HEADER_DOC_ID = "CouchDbId";
    @Metadata(description = "The couchdb document revision", javaType = "String")
    String HEADER_DOC_REV = "CouchDbRev";
    @Metadata(description = "The method (delete / update)", javaType = "String")
    String HEADER_METHOD = "CouchDbMethod";

    @Metadata(label = "consumer", description = "The resume action to execute when resuming.", javaType = "String")
    String COUCHDB_RESUME_ACTION = "CamelCouchDbResumeAction";

}
