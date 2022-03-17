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
package org.apache.camel.component.cmis;

import org.apache.camel.spi.Metadata;
import org.apache.chemistry.opencmis.commons.PropertyIds;

public interface CamelCMISConstants {
    String CMIS_DOCUMENT = "cmis:document";
    String CMIS_FOLDER = "cmis:folder";
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.cmis.CamelCMISActions")
    String CMIS_ACTION = "cmis:action";
    @Metadata(label = "producer", description = "If `CamelCMISFolderPath` is not set, will try to find out the path of\n" +
                                                "the node from this cmis property and it is name",
              javaType = "String")
    String PATH = PropertyIds.PATH;
    @Metadata(label = "producer", description = "If `CamelCMISFolderPath` is not set, will try to find out the path of\n" +
                                                "the node from this cmis property and it is path",
              javaType = "String")
    String NAME = PropertyIds.NAME;
    @Metadata(label = "producer", description = "The type of the node", javaType = "String")
    String OBJECT_TYPE_ID = PropertyIds.OBJECT_TYPE_ID;
    @Metadata(label = "producer", description = "The check-in comment for the document version", javaType = "String")
    String CHECKIN_COMMENT = PropertyIds.CHECKIN_COMMENT;
    @Metadata(label = "producer", description = "The mimetype to set for a document", javaType = "String")
    String CONTENT_STREAM_MIME_TYPE = PropertyIds.CONTENT_STREAM_MIME_TYPE;
    @Metadata(label = "producer", description = "The current folder to use during the execution. If not specified will\n" +
                                                "use the root folder",
              javaType = "String", defaultValue = "/")
    String CMIS_FOLDER_PATH = "CamelCMISFolderPath";
    @Metadata(label = "producer", description = "The id of the object", javaType = "String")
    String CMIS_OBJECT_ID = "CamelCMISObjectId";
    @Metadata(label = "producer", description = "The id of the destination folder", javaType = "String")
    String CMIS_DESTIONATION_FOLDER_ID = "CamelCMISDestinationFolderId";
    @Metadata(label = "producer", description = "The id of the source folder", javaType = "String")
    String CMIS_SOURCE_FOLDER_ID = "CamelCMISSourceFolderId";
    String CMIS_DOCUMENT_PATH = "CamelCMISDocumentPath";
    @Metadata(label = "producer", description = "Number of nodes returned from the query", javaType = "Integer")
    String CAMEL_CMIS_RESULT_COUNT = "CamelCMISResultCount";
    @Metadata(label = "producer", description = "In `queryMode` this header will force the producer to retrieve the\n" +
                                                "content of document nodes.",
              javaType = "Boolean")
    String CAMEL_CMIS_RETRIEVE_CONTENT = "CamelCMISRetrieveContent";
    @Metadata(label = "producer", description = "Max number of nodes to read.", javaType = "Integer")
    String CAMEL_CMIS_READ_SIZE = "CamelCMISReadSize";
    String CAMEL_CMIS_CONTENT_STREAM = "CamelCMISContent";
    @Metadata(label = "producer", description = "Apply only to this version (false) or all versions (true)",
              javaType = "Boolean")
    String ALL_VERSIONS = "CamelCMISAllVersions";
    @Metadata(label = "producer", description = "The versioning state", javaType = "String")
    String VERSIONING_STATE = "cmis:versioningState";
}
