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
package org.apache.camel.component.odata;

import org.apache.camel.spi.Metadata;

public final class ODataConstants {

    @Metadata(description = "The OData operation to perform",
              javaType = "org.apache.camel.component.odata.ODataOperation")
    public static final String OPERATION = "CamelODataOperation";

    @Metadata(description = "The OData entity key",
              javaType = "String")
    public static final String KEY = "CamelODataKey";

    @Metadata(description = "The OData ETag",
              javaType = "String")
    public static final String ETAG = "CamelODataETag";

    @Metadata(description = "The OData next link for pagination",
              javaType = "String")
    public static final String NEXT_LINK = "CamelODataNextLink";

    @Metadata(description = "Whether to include the OData count in the response",
              javaType = "Boolean")
    public static final String INCLUDE_COUNT = "CamelODataIncludeCount";

    @Metadata(description = "The OData count returned by the server",
              javaType = "Long")
    public static final String COUNT = "CamelODataCount";

    @Metadata(description = "The OData filter expression",
              javaType = "String")
    public static final String FILTER = "CamelODataFilter";

    @Metadata(description = "The OData select expression",
              javaType = "String")
    public static final String SELECT = "CamelODataSelect";

    @Metadata(description = "The OData expand expression",
              javaType = "String")
    public static final String EXPAND = "CamelODataExpand";

    @Metadata(description = "The OData orderby expression",
              javaType = "String")
    public static final String ORDER_BY = "CamelODataOrderBy";

    @Metadata(description = "The OData top query option",
              javaType = "Integer")
    public static final String TOP = "CamelODataTop";

    @Metadata(description = "The OData skip query option",
              javaType = "Integer")
    public static final String SKIP = "CamelODataSkip";

    private ODataConstants() {
    }
}
