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
package org.apache.camel.component.alibaba.ots.constants;

import org.apache.camel.spi.Metadata;

public final class AlibabaOtsHeaders {

    @Metadata(label = "producer",
              description = "The operation that the producer performs. This header replaces the operation option.",
              javaType = "String")
    public static final String OPERATION = AlibabaOtsProperties.OPERATION;

    @Metadata(label = "producer",
              description = "The request ID that Tablestore returns in the response to the operation. The producer sets it when"
                            + " the operation succeeds.",
              javaType = "String")
    public static final String REQUEST_ID = AlibabaOtsProperties.REQUEST_ID;

    private AlibabaOtsHeaders() {
    }
}
