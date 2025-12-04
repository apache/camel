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

package org.apache.camel.component.ibm.watson.discovery;

import org.apache.camel.spi.Metadata;

public interface WatsonDiscoveryConstants {

    @Metadata(description = "The operation to perform", javaType = "WatsonDiscoveryOperations")
    String OPERATION = "CamelIBMWatsonDiscoveryOperation";

    // Query parameters
    @Metadata(description = "The natural language query to execute", javaType = "String")
    String QUERY = "CamelIBMWatsonDiscoveryQuery";

    @Metadata(description = "The number of results to return", javaType = "Integer")
    String COUNT = "CamelIBMWatsonDiscoveryCount";

    @Metadata(description = "The filter to apply to the query", javaType = "String")
    String FILTER = "CamelIBMWatsonDiscoveryFilter";

    // Collection parameters
    @Metadata(description = "The collection ID", javaType = "String")
    String COLLECTION_ID = "CamelIBMWatsonDiscoveryCollectionId";

    @Metadata(description = "The collection name", javaType = "String")
    String COLLECTION_NAME = "CamelIBMWatsonDiscoveryCollectionName";

    @Metadata(description = "The collection description", javaType = "String")
    String COLLECTION_DESCRIPTION = "CamelIBMWatsonDiscoveryCollectionDescription";

    // Document parameters
    @Metadata(description = "The document ID", javaType = "String")
    String DOCUMENT_ID = "CamelIBMWatsonDiscoveryDocumentId";

    @Metadata(description = "The document file", javaType = "java.io.InputStream")
    String DOCUMENT_FILE = "CamelIBMWatsonDiscoveryDocumentFile";

    @Metadata(description = "The document filename", javaType = "String")
    String DOCUMENT_FILENAME = "CamelIBMWatsonDiscoveryDocumentFilename";

    @Metadata(description = "The document content type", javaType = "String")
    String DOCUMENT_CONTENT_TYPE = "CamelIBMWatsonDiscoveryDocumentContentType";
}
