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
package org.apache.camel.component.file.watch.constants;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

import io.methvin.watcher.DirectoryChangeEvent;

public enum FileEventEnum {
    CREATE,
    DELETE,
    MODIFY;

    public static FileEventEnum valueOf(DirectoryChangeEvent directoryChangeEvent) {
        return valueOf(directoryChangeEvent.eventType());
    }

    public static FileEventEnum valueOf(DirectoryChangeEvent.EventType directoryChangeEventType) {
        switch (directoryChangeEventType) {
            case CREATE:
                return FileEventEnum.CREATE;
            case DELETE:
                return FileEventEnum.DELETE;
            case MODIFY:
                return FileEventEnum.MODIFY;
            default:
                return null;
        }
    }

    public static FileEventEnum valueOf(WatchEvent<?> watchEvent) {
        if (watchEvent.context() instanceof Path) {
            return valueOf(watchEvent.kind());
        }
        return null;
    }

    public static FileEventEnum valueOf(WatchEvent.Kind<?> watchEventKind) {
        if (watchEventKind == null) {
            return null;
        } else if (watchEventKind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
            return FileEventEnum.CREATE;
        } else if (watchEventKind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            return FileEventEnum.DELETE;
        } else if (watchEventKind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            return FileEventEnum.MODIFY;
        }
        return null;
    }

    public WatchEvent.Kind<Path> kind() {
        switch (this) {
            case CREATE:
                return StandardWatchEventKinds.ENTRY_CREATE;
            case MODIFY:
                return StandardWatchEventKinds.ENTRY_MODIFY;
            case DELETE:
                return StandardWatchEventKinds.ENTRY_DELETE;
            default:
                return null;
        }
    }

    public DirectoryChangeEvent.EventType eventType() {
        switch (this) {
            case CREATE:
                return DirectoryChangeEvent.EventType.CREATE;
            case MODIFY:
                return DirectoryChangeEvent.EventType.MODIFY;
            case DELETE:
                return DirectoryChangeEvent.EventType.DELETE;
            default:
                return null;
        }
    }
}
