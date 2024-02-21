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
package org.apache.camel.oaipmh.component.model;

import org.apache.camel.spi.Metadata;

public final class OAIPMHConstants {
    @Metadata(label = "producer", description = "This header is obtained when onlyFirst option is enable. " +
                                                "Return resumption token of the request when data is still available.",
              javaType = "String")
    public static final String RESUMPTION_TOKEN = "CamelOaimphResumptionToken";
    public static final String URL = "CamelOaimphUrl";
    public static final String ENDPOINT_URL = "CamelOaimphEndpointUrl";
    public static final String VERB = "CamelOaimphVerb";
    public static final String METADATA_PREFIX = "CamelOaimphMetadataPrefix";
    public static final String ONLY_FIRST = "CamelOaimphOnlyFirst";
    public static final String IGNORE_SSL_WARNINGS = "CamelOaimphIgnoreSSLWarnings";
    public static final String UNTIL = "CamelOaimphUntil";
    public static final String FROM = "CamelOaimphFrom";
    public static final String SET = "CamelOaimphSet";
    public static final String IDENTIFIER = "CamelOaimphIdentifier";

    private OAIPMHConstants() {
    }

}
