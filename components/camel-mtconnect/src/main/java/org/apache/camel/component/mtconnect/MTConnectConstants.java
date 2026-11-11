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
package org.apache.camel.component.mtconnect;

import org.apache.camel.spi.Metadata;

/**
 * The headers of consumer messages and producer requests and replies, and the values of the quality, reason, document,
 * resync and operation headers.
 */
public final class MTConnectConstants {

    @Metadata(label = "consumer", description = "The agent address of the endpoint, which tells apart agents that serve"
                                                + " the same device uuid.",
              javaType = "String")
    public static final String AGENT = "CamelMTConnectAgent";
    @Metadata(label = "consumer", description = "The uuid of the device.", javaType = "String")
    public static final String DEVICE_UUID = "CamelMTConnectDeviceUuid";
    @Metadata(label = "consumer", description = "The name of the device.", javaType = "String")
    public static final String DEVICE_NAME = "CamelMTConnectDeviceName";
    @Metadata(label = "consumer", description = "The id of the component that owns the data item.", javaType = "String")
    public static final String COMPONENT_ID = "CamelMTConnectComponentId";
    @Metadata(label = "consumer", description = "The type of the component that owns the data item, such as Linear,"
                                                + " Controller or Device.",
              javaType = "String")
    public static final String COMPONENT_TYPE = "CamelMTConnectComponentType";
    @Metadata(label = "consumer", description = "The name of the component that owns the data item.", javaType = "String")
    public static final String COMPONENT_NAME = "CamelMTConnectComponentName";
    @Metadata(label = "consumer", description = "The data item id, unique within the agent.", javaType = "String")
    public static final String DATA_ITEM_ID = "CamelMTConnectDataItemId";
    @Metadata(label = "consumer", description = "The data item name.", javaType = "String")
    public static final String DATA_ITEM_NAME = "CamelMTConnectDataItemName";
    @Metadata(label = "consumer", description = "The data item category from the probe.", javaType = "String",
              enums = "SAMPLE,EVENT,CONDITION")
    public static final String CATEGORY = "CamelMTConnectCategory";
    @Metadata(label = "consumer", description = "The data item type from the probe, such as POSITION or EXECUTION.",
              javaType = "String")
    public static final String TYPE = "CamelMTConnectType";
    @Metadata(label = "consumer", description = "The data item subtype, such as ACTUAL.", javaType = "String")
    public static final String SUB_TYPE = "CamelMTConnectSubType";
    @Metadata(label = "consumer", description = "The units of the value from the probe.", javaType = "String")
    public static final String UNITS = "CamelMTConnectUnits";
    @Metadata(label = "consumer", description = "The native units of the data item from the probe.", javaType = "String")
    public static final String NATIVE_UNITS = "CamelMTConnectNativeUnits";
    @Metadata(label = "consumer", description = "The data item representation from the probe; VALUE when the probe"
                                                + " names none.",
              javaType = "String", enums = "VALUE,DATA_SET,TABLE,TIME_SERIES,DISCRETE")
    public static final String REPRESENTATION = "CamelMTConnectRepresentation";
    @Metadata(label = "consumer", description = "The sequence number the agent gave the observation.", javaType = "Long")
    public static final String SEQUENCE = "CamelMTConnectSequence";
    @Metadata(label = "consumer", description = "The observation timestamp, the time the failure was detected for a Bad"
                                                + " message without an observation, or on asset messages the asset's"
                                                + " timestamp: that of its AssetRemoved observation when the agent no"
                                                + " longer holds it.",
              javaType = "java.time.Instant")
    public static final String TIMESTAMP = "CamelMTConnectTimestamp";
    @Metadata(label = "common", description = "The instance id of the agent; it changes when the agent restarts. Set on"
                                              + " consumer messages and producer replies.",
              javaType = "Long")
    public static final String INSTANCE_ID = "CamelMTConnectInstanceId";
    @Metadata(label = "consumer", description = "Good, or Bad when the agent reports the value UNAVAILABLE or cannot be"
                                                + " read.",
              javaType = "String", enums = "Good,Bad")
    public static final String QUALITY = "CamelMTConnectQuality";
    @Metadata(label = "consumer", description = "Why the quality is Bad: the agent reports UNAVAILABLE, the agent is"
                                                + " unreachable, or the agent answered with an error.",
              javaType = "String", enums = "UNAVAILABLE,UNREACHABLE,ERROR")
    public static final String REASON = "CamelMTConnectReason";
    @Metadata(label = "consumer", description = "The failure behind a Bad message without an observation: the HTTP"
                                                + " status, the parser message or the transport error.",
              javaType = "String")
    public static final String ERROR = "CamelMTConnectError";
    @Metadata(label = "consumer", description = "The level of a condition.", javaType = "String",
              enums = "Normal,Warning,Fault,Unavailable")
    public static final String LEVEL = "CamelMTConnectLevel";
    @Metadata(label = "consumer", description = "The native code of a condition.", javaType = "String")
    public static final String NATIVE_CODE = "CamelMTConnectNativeCode";
    @Metadata(label = "consumer", description = "The native severity of a condition.", javaType = "String")
    public static final String NATIVE_SEVERITY = "CamelMTConnectNativeSeverity";
    @Metadata(label = "consumer", description = "The qualifier of a condition, such as HIGH or LOW.", javaType = "String")
    public static final String QUALIFIER = "CamelMTConnectQualifier";
    @Metadata(label = "consumer", description = "The agent document the message came from: Current or Sample for an"
                                                + " observation, Asset for an asset message.",
              javaType = "String", enums = "Current,Sample,Asset")
    public static final String DOCUMENT = "CamelMTConnectDocument";
    @Metadata(label = "consumer", description = "Set on the Current messages that follow a resynchronisation: the"
                                                + " agent's buffer no longer held the next observation, or the agent"
                                                + " restarted.",
              javaType = "String", enums = "OUT_OF_RANGE,INSTANCE_CHANGED")
    public static final String RESYNC = "CamelMTConnectResync";
    @Metadata(label = "consumer", description = "The compositionId attribute of the observation: the id of the component"
                                                + " composition the data item belongs to.",
              javaType = "String")
    public static final String COMPOSITION_ID = "CamelMTConnectCompositionId";
    @Metadata(label = "consumer", description = "The resetTriggered attribute of the observation, such as MANUAL or"
                                                + " DAY: the value was reset, so it does not continue the previous one. A"
                                                + " data set or table observation with it is the whole set, not a change.",
              javaType = "String")
    public static final String RESET_TRIGGERED = "CamelMTConnectResetTriggered";
    @Metadata(label = "consumer", description = "The statistic attribute of the observation, such as AVERAGE or MAXIMUM.",
              javaType = "String")
    public static final String STATISTIC = "CamelMTConnectStatistic";
    @Metadata(label = "consumer", description = "The duration attribute of the observation: the time in seconds the"
                                                + " statistic covers.",
              javaType = "java.math.BigDecimal")
    public static final String DURATION = "CamelMTConnectDuration";
    @Metadata(label = "consumer", description = "The sampleRate attribute of a time series observation: samples per"
                                                + " second.",
              javaType = "java.math.BigDecimal")
    public static final String SAMPLE_RATE = "CamelMTConnectSampleRate";
    @Metadata(label = "common", description = "The asset type, such as CuttingTool: on the consumer the type of an asset"
                                              + " message, or the assetType attribute of an AssetChanged or AssetRemoved"
                                              + " observation; on the producer the type of the assets request,"
                                              + " overriding the assetType option for one message.",
              javaType = "String")
    public static final String ASSET_TYPE = "CamelMTConnectAssetType";
    @Metadata(label = "common", description = "The asset id: on the consumer the id of an asset message; on the"
                                              + " producer the asset the assets request reads, or several ids separated"
                                              + " by a semicolon, with which assetType, count and removed do not apply.",
              javaType = "String")
    public static final String ASSET_ID = "CamelMTConnectAssetId";
    @Metadata(label = "consumer", description = "Whether the asset message reports a removed asset: the agent marked it"
                                                + " removed, or reported AssetRemoved for it. The body is null when the"
                                                + " agent no longer holds the asset.",
              javaType = "Boolean")
    public static final String ASSET_REMOVED = "CamelMTConnectAssetRemoved";
    @Metadata(label = "producer", description = "The request to send, overriding the operation option for one message.",
              javaType = "String", enums = "probe,current,sample,assets")
    public static final String OPERATION = "CamelMTConnectOperation";
    @Metadata(label = "producer", description = "The sequence at which the current request reads the values, overriding"
                                                + " the at option for one message.",
              javaType = "Long")
    public static final String AT = "CamelMTConnectAt";
    @Metadata(label = "producer", description = "The first sequence of the sample request, overriding the from option for"
                                                + " one message.",
              javaType = "Long")
    public static final String FROM = "CamelMTConnectFrom";
    @Metadata(label = "producer", description = "The last sequence the sample request includes, overriding the to option"
                                                + " for one message.",
              javaType = "Long")
    public static final String TO = "CamelMTConnectTo";
    @Metadata(label = "producer", description = "The maximum number of observations of the sample request or of assets"
                                                + " of the assets request, overriding the count option for one message.",
              javaType = "Integer")
    public static final String COUNT = "CamelMTConnectCount";
    @Metadata(label = "producer", description = "The oldest sequence the agent still buffers, from the reply's header.",
              javaType = "Long")
    public static final String FIRST_SEQUENCE = "CamelMTConnectFirstSequence";
    @Metadata(label = "producer", description = "The newest sequence the agent holds, from the reply's header.",
              javaType = "Long")
    public static final String LAST_SEQUENCE = "CamelMTConnectLastSequence";
    @Metadata(label = "producer", description = "The sequence that follows the reply: the from of the next sample request"
                                                + " that continues it.",
              javaType = "Long")
    public static final String NEXT_SEQUENCE = "CamelMTConnectNextSequence";

    public static final String QUALITY_GOOD = "Good";
    public static final String QUALITY_BAD = "Bad";
    public static final String REASON_UNAVAILABLE = "UNAVAILABLE";
    public static final String REASON_UNREACHABLE = "UNREACHABLE";
    public static final String REASON_ERROR = "ERROR";
    public static final String DOCUMENT_CURRENT = "Current";
    public static final String DOCUMENT_SAMPLE = "Sample";
    public static final String DOCUMENT_ASSET = "Asset";
    public static final String RESYNC_OUT_OF_RANGE = "OUT_OF_RANGE";
    public static final String RESYNC_INSTANCE_CHANGED = "INSTANCE_CHANGED";

    private MTConnectConstants() {
    }
}
