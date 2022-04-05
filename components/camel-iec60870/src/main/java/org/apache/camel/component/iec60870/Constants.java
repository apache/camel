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
package org.apache.camel.component.iec60870;

import org.apache.camel.spi.Metadata;

public interface Constants {

    String SCHEME_SERVER = "iec60870-server";
    String SCHEME_CLIENT = "iec60870-client";

    String PARAM_DATA_MODULE_OPTIONS = "dataModuleOptions";

    String PARAM_PROTOCOL_OPTIONS = "protocolOptions";

    String PARAM_CONNECTION_OPTIONS = "connectionOptions";

    @Metadata(label = "consumer", description = "The value", javaType = "Object", applicableFor = SCHEME_CLIENT)
    String IEC60870_VALUE = "CamelIec60870Value";
    @Metadata(label = "consumer", description = "The timestamp of the value", javaType = "long", applicableFor = SCHEME_CLIENT)
    String IEC60870_TIMESTAMP = "CamelIec60870Timestamp";
    @Metadata(label = "consumer", description = "The quality information of the value",
              javaType = "org.eclipse.neoscada.protocol.iec60870.asdu.types.QualityInformation", applicableFor = SCHEME_CLIENT)
    String IEC60870_QUALITY = "CamelIec60870Quality";
    @Metadata(label = "consumer", description = "Is overflow", javaType = "boolean", applicableFor = SCHEME_CLIENT)
    String IEC60870_OVERFLOW = "CamelIec60870Overflow";
    @Metadata(label = "consumer", description = "The address as ObjectAddress",
              javaType = "org.apache.camel.component.iec60870.ObjectAddress", applicableFor = SCHEME_SERVER)
    String ADDRESS = "address";
    @Metadata(label = "consumer", description = "The value", javaType = "Object", applicableFor = SCHEME_SERVER)
    String VALUE = "value";
    @Metadata(label = "consumer", description = "The address as InformationObjectAddress",
              javaType = "org.eclipse.neoscada.protocol.iec60870.asdu.types.InformationObjectAddress",
              applicableFor = SCHEME_SERVER)
    String INFORMATION_OBJECT_ADDRESS = "informationObjectAddress";
    @Metadata(label = "consumer", description = "The ASDU header",
              javaType = "org.eclipse.neoscada.protocol.iec60870.asdu.ASDUHeader", applicableFor = SCHEME_SERVER)
    String ASDU_HEADER = "asduHeader";
    @Metadata(label = "consumer", description = "The type", javaType = "byte", applicableFor = SCHEME_SERVER)
    String TYPE = "type";
    @Metadata(label = "consumer", description = "Is execute", javaType = "boolean", applicableFor = SCHEME_SERVER)
    String EXECUTE = "execute";
}
