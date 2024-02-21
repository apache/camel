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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.spi.Metadata;

public enum SomeEnumConstants {
    @Deprecated
    @Metadata(description = "key full desc", label = "my label", displayName = "my display name",
              javaType = "org.apache.camel.maven.packaging.endpoint.SomeEndpoint$MyEnum", required = true,
              defaultValue = "VAL1", deprecationNote = "my deprecated note", secret = true)
    KEY_FULL,
    @Metadata
    KEY_EMPTY,
    /**
     * Some description
     */
    @Metadata
    KEY_EMPTY_WITH_JAVA_DOC;
}
