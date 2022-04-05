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
    @Metadata(description = "The operation to perform",
              javaType = "org.apache.camel.component.google.functions.GoogleCloudFunctionsOperations")
    String OPERATION = "GoogleCloudFunctionsOperation";
    @Metadata(description = "The name of the function (as defined in source code) that will be executed. Used for createFunction operation",
              javaType = "String")
    String ENTRY_POINT = "GoogleCloudFunctionsEntryPoint";
    @Metadata(description = "The runtime in which to run the function.\n\nPossible values are:\n\n" +
                            "* `nodejs10`\n* `nodejs12`\n* `nodejs14`\n* `python37`\n* `python38`\n* `python39`\n* `go111`\n* `go113`\n"
                            +
                            "* `java11`\n* `dotnet3`\n* `ruby26`\n* `nodejs6`\n* `nodejs8`\n" +
                            "\nUsed for createFunction operation.",
              javaType = "String")
    String RUNTIME = "GoogleCloudFunctionsRuntime";
    @Metadata(description = "The Google Cloud Storage URL, starting with `gs://`, pointing to the zip archive which contains the function. Used for createFunction operation.",
              javaType = "String")
    String SOURCE_ARCHIVE_URL = "GoogleCloudFunctionsSourceArchiveUrl";
    @Metadata(description = "The response object resulting from the Google Functions Client invocation", javaType = "Object")
    String RESPONSE_OBJECT = "GoogleCloudFunctionsResponseObject";
}
