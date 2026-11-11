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

import java.time.Instant;

import org.apache.camel.Message;

/**
 * Fills a consumer message from an asset: the asset element as a standalone XML document as the body, or no body when
 * the agent no longer holds a removed asset, and its identity as {@code CamelMTConnect*} headers. Asset messages carry
 * no quality, because assets are not observations.
 */
final class AssetMessages {

    private AssetMessages() {
    }

    /**
     * @param removed whether the agent marked the asset removed or reported AssetRemoved for it
     */
    static void fill(
            Message message, String agent, AgentDocument.Asset asset, boolean removed, String deviceName,
            Long instanceId) {
        message.setBody(asset.xml());
        headers(message, agent, asset.assetId(), asset.type(), removed, asset.deviceUuid(), deviceName,
                asset.timestamp(), instanceId);
    }

    /** Fills the message of a removed asset the agent no longer holds, from its AssetRemoved observation. */
    static void fillGone(Message message, String agent, String assetId, Observation removal, Long instanceId) {
        message.setBody(null);
        headers(message, agent, assetId, removal.attributes().assetType(), true,
                removal.deviceUuid(), removal.deviceName(), removal.timestamp(), instanceId);
    }

    private static void headers(
            Message message, String agent, String assetId, String type, boolean removed, String deviceUuid,
            String deviceName, Instant timestamp, Long instanceId) {
        header(message, MTConnectConstants.AGENT, agent);
        header(message, MTConnectConstants.ASSET_ID, assetId);
        header(message, MTConnectConstants.ASSET_TYPE, type);
        header(message, MTConnectConstants.ASSET_REMOVED, removed);
        header(message, MTConnectConstants.DEVICE_UUID, deviceUuid);
        header(message, MTConnectConstants.DEVICE_NAME, deviceName);
        header(message, MTConnectConstants.TIMESTAMP, timestamp);
        header(message, MTConnectConstants.INSTANCE_ID, instanceId);
        header(message, MTConnectConstants.DOCUMENT, MTConnectConstants.DOCUMENT_ASSET);
    }

    private static void header(Message message, String name, Object value) {
        if (value != null) {
            message.setHeader(name, value);
        }
    }
}
