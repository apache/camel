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
package org.apache.camel.component.sap.netweaver;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class NetWeaverConstants {

    @Metadata(description = "The command to execute in\n" +
                            "http://msdn.microsoft.com/en-us/library/cc956153.aspx[MS ADO.Net Data\n" +
                            "Service] format.",
              javaType = "String", required = true)
    public static final String COMMAND = "CamelNetWeaverCommand";
    @Metadata(description = "The http path.", javaType = "String")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "The media type.", javaType = "String")
    public static final String ACCEPT = "Accept";

    private NetWeaverConstants() {
    }
}
