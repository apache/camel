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
package org.apache.camel.component.rest.postman;

import org.apache.camel.spi.Metadata;

/**
 * Headers set by the {@code rest-postman} component.
 */
public final class RestPostmanConstants {

    @Metadata(description = "The id of the Postman request being invoked or serviced.", javaType = "String")
    public static final String REQUEST_ID = "CamelRestPostmanRequestId";

    @Metadata(description = "The name of the Postman request, as written in the collection.", javaType = "String")
    public static final String REQUEST_NAME = "CamelRestPostmanRequestName";

    @Metadata(description = "The folder path of the Postman request, with folders separated by a slash.",
              javaType = "String")
    public static final String FOLDER_PATH = "CamelRestPostmanFolderPath";

    @Metadata(description = "The number of requests executed when running a folder or a whole collection.",
              javaType = "Integer")
    public static final String REQUEST_COUNT = "CamelRestPostmanRequestCount";

    @Metadata(description = "The number of requests that failed when running a folder or a whole collection with"
                            + " runFailFast disabled.",
              javaType = "Integer")
    public static final String FAILED_COUNT = "CamelRestPostmanFailedCount";

    private RestPostmanConstants() {
    }
}
