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

import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.map.InsertedEvent;
import net.openhft.chronicle.engine.map.RemovedEvent;
import net.openhft.chronicle.engine.map.UpdatedEvent;


public enum ChronicleEngineMapEventType {
    INSERT(InsertedEvent.class),
    UPDATE(UpdatedEvent.class),
    REMOVE(RemovedEvent.class);

    private static final ChronicleEngineMapEventType[] VALUES = values();
    private final Class<? extends MapEvent> type;

    ChronicleEngineMapEventType(Class<? extends MapEvent> type) {
        this.type = type;
    }

    public Class<? extends MapEvent> getType() {
        return this.type;
    }

    public static Class<? extends MapEvent> getType(String name) {
        return valueOf(name.toUpperCase()).getType();
    }

    public static ChronicleEngineMapEventType fromEvent(MapEvent event) {
        if (event instanceof InsertedEvent) {
            return ChronicleEngineMapEventType.INSERT;
        }
        if (event instanceof UpdatedEvent) {
            return ChronicleEngineMapEventType.UPDATE;
        }
        if (event instanceof RemovedEvent) {
            return ChronicleEngineMapEventType.REMOVE;
        }

        throw new IllegalArgumentException("Unknown event type: " + event.getClass());
    }
}
