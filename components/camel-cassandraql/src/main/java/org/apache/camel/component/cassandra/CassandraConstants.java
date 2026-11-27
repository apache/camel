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
package org.apache.camel.component.cassandra;

import org.apache.camel.spi.Metadata;

/**
 * Cassandra QL Endpoint constants
 */
public final class CassandraConstants {

    @Metadata(label = "producer",
              description = "The CQL statement that the producer executes instead of the cql option: a String, or a"
                            + " com.datastax.oss.driver.api.core.cql.SimpleStatement. An empty String uses the cql option."
                            + " With prepareStatements, the statement is prepared for each message.",
              javaType = "String")
    public static final String CQL_QUERY = "CamelCqlQuery";

    @Metadata(label = "consumer",
              description = "Not a message header: the name of the org.apache.camel.resume.ResumeAction bean that the"
                            + " consumer looks up in the registry when it starts with a resume strategy whose adapter takes"
                            + " a resume action.",
              javaType = "String")
    public static final String CASSANDRA_RESUME_ACTION = "CamelCqlResumeAction";

    private CassandraConstants() {
    }

}
