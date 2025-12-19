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

    // Connection state headers
    @Metadata(label = "consumer", description = "The connection state (CONNECTED, DISCONNECTED, etc.)",
              javaType = "org.eclipse.neoscada.protocol.iec60870.client.AutoConnectClient.State", applicableFor = SCHEME_CLIENT)
    String IEC60870_CONNECTION_STATE = "CamelIec60870ConnectionState";
    @Metadata(label = "consumer", description = "The connection state error if any",
              javaType = "Throwable", applicableFor = SCHEME_CLIENT)
    String IEC60870_CONNECTION_ERROR = "CamelIec60870ConnectionError";
    @Metadata(label = "consumer", description = "Connection uptime in milliseconds since last connected",
              javaType = "long", applicableFor = SCHEME_CLIENT)
    String IEC60870_CONNECTION_UPTIME = "CamelIec60870ConnectionUptime";

    // Producer command types
    @Metadata(label = "producer",
              description = "The command type: 'value' (default), 'interrogation', 'read', or 'status'",
              javaType = "String", applicableFor = SCHEME_CLIENT)
    String IEC60870_COMMAND_TYPE = "CamelIec60870CommandType";

    // Command type values
    String COMMAND_TYPE_VALUE = "value";
    String COMMAND_TYPE_INTERROGATION = "interrogation";
    String COMMAND_TYPE_READ = "read";
    String COMMAND_TYPE_STATUS = "status";

    // Interrogation headers
    @Metadata(label = "producer", description = "The ASDU address for interrogation (optional, defaults to broadcast)",
              javaType = "org.eclipse.neoscada.protocol.iec60870.asdu.types.ASDUAddress", applicableFor = SCHEME_CLIENT)
    String IEC60870_ASDU_ADDRESS = "CamelIec60870AsduAddress";
    @Metadata(label = "producer", description = "The qualifier of interrogation: 20 (global) or 21-36 (groups 1-16)",
              javaType = "short", applicableFor = SCHEME_CLIENT)
    String IEC60870_QOI = "CamelIec60870Qoi";

    // Individual quality flag headers
    @Metadata(label = "consumer", description = "Quality flag: Blocked (BL)", javaType = "boolean",
              applicableFor = SCHEME_CLIENT)
    String IEC60870_QUALITY_BLOCKED = "CamelIec60870QualityBlocked";
    @Metadata(label = "consumer", description = "Quality flag: Substituted (SB)", javaType = "boolean",
              applicableFor = SCHEME_CLIENT)
    String IEC60870_QUALITY_SUBSTITUTED = "CamelIec60870QualitySubstituted";
    @Metadata(label = "consumer", description = "Quality flag: Not topical (NT)", javaType = "boolean",
              applicableFor = SCHEME_CLIENT)
    String IEC60870_QUALITY_NOT_TOPICAL = "CamelIec60870QualityNotTopical";
    @Metadata(label = "consumer", description = "Quality flag: Invalid (IV)", javaType = "boolean",
              applicableFor = SCHEME_CLIENT)
    String IEC60870_QUALITY_VALID = "CamelIec60870QualityValid";

    // Cause of transmission header
    @Metadata(label = "consumer", description = "The cause of transmission",
              javaType = "org.eclipse.neoscada.protocol.iec60870.asdu.types.CauseOfTransmission", applicableFor = SCHEME_CLIENT)
    String IEC60870_CAUSE_OF_TRANSMISSION = "CamelIec60870CauseOfTransmission";
}
