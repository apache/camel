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
package org.apache.camel.component.nagios;

import org.apache.camel.spi.Metadata;

public final class NagiosConstants {

    @Metadata(description = "The hostname to be sent in this passive check.", javaType = "String", defaultValue = "localhost")
    public static final String HOST_NAME = "CamelNagiosHostName";
    @Metadata(description = "The level of the passive check.", javaType = "com.googlecode.jsendnsca.Level")
    public static final String LEVEL = "CamelNagiosLevel";
    @Metadata(description = "The service name.", javaType = "String", defaultValue = "The context name")
    public static final String SERVICE_NAME = "CamelNagiosServiceName";

    private NagiosConstants() {
    }

}
