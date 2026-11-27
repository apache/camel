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
package org.apache.camel.component.google.functions;

import org.apache.camel.spi.Metadata;

public interface GoogleCloudFunctionsConstants {
    @Metadata(description = "The operation to perform, which overrides the operation option.",
              javaType = "org.apache.camel.component.google.functions.GoogleCloudFunctionsOperations")
    String OPERATION = "CamelGoogleCloudFunctionsOperation";
    @Metadata(description = "The name of the function in the source code that runs when the new function is called, such as"
                            + " the exported function for Node.js or the fully qualified class name for Java. createFunction"
                            + " requires it, unless pojoRequest=true.",
              javaType = "String")
    String ENTRY_POINT = "CamelGoogleCloudFunctionsEntryPoint";
    @Metadata(description = "The runtime ID of the new function, for example nodejs20, python312 or java17. createFunction"
                            + " requires it, unless pojoRequest=true. Google lists the supported runtime IDs in the Cloud"
                            + " Functions documentation.",
              javaType = "String")
    String RUNTIME = "CamelGoogleCloudFunctionsRuntime";
    @Metadata(description = "The Cloud Storage URL, starting with gs://, of the ZIP archive that holds the source code of the"
                            + " new function. createFunction requires it, unless pojoRequest=true, and creates the function"
                            + " with an HTTPS trigger.",
              javaType = "String")
    String SOURCE_ARCHIVE_URL = "CamelGoogleCloudFunctionsSourceArchiveUrl";
    @Metadata(description = "The full response of the service: the CallFunctionResponse for callFunction, the"
                            + " GenerateDownloadUrlResponse for generateDownloadUrl, or the GenerateUploadUrlResponse for"
                            + " generateUploadUrl. The message body holds only the result or the signed URL.",
              javaType = "Object")
    String RESPONSE_OBJECT = "CamelGoogleCloudFunctionsResponseObject";
}
