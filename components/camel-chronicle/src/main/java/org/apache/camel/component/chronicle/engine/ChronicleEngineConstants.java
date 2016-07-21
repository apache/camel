/**
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
package org.apache.camel.component.chronicle.engine;

public final class ChronicleEngineConstants {

    // Headers
    public static final String ASSET_NAME = "ChronicleEngineAssetName";
    public static final String PATH = "ChronicleEnginePath";
    public static final String TOPIC = "ChronicleEngineTopic";
    public static final String TOPOLOGICAL_EVENT_NAME = "ChronicleEngineTopologicalEventName";
    public static final String TOPOLOGICAL_EVENT_FULL_NAME = "ChronicleEngineTopologicalEventFullName";
    public static final String TOPOLOGICAL_EVENT_ADDED = "ChronicleEngineTopologicalEventAdded";
    public static final String MAP_EVENT_TYPE = "ChronicleEngineMapEventType";
    public static final String RESULT = "ChronicleEngineResult";
    public static final String QUEUE_INDEX = "ChronicleEngineQueueIndex";
    public static final String KEY = "ChronicleEngineKey";
    public static final String VALUE = "ChronicleEngineValue";
    public static final String DEFAULT_VALUE = "ChronicleEngineDefaultValue";
    public static final String OLD_VALUE = "ChronicleEngineOldValue";
    public static final String ACTION = "ChronicleEngineAction";

    // Actions
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_PUBLISH_AND_INDEX = "PUBLISH_AND_INDEX";
    public static final String ACTION_PUT = "PUT";
    public static final String ACTION_GET_AND_PUT = "GET_AND_PUT";
    public static final String ACTION_PUT_ALL = "PUT_ALL";
    public static final String ACTION_PUT_IF_ABSENT = "PUT_IF_ABSENT";
    public static final String ACTION_GET = "GET";
    public static final String ACTION_GET_AND_REMOVE = "GET_AND_REMOVE";
    public static final String ACTION_REMOVE = "REMOVE";
    public static final String ACTION_IS_EMPTY = "IS_EMPTY";
    public static final String ACTION_IS_SIZE = "SIZE";

    private ChronicleEngineConstants() {
    }
}
