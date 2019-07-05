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
import java.util.Date;

import io.methvin.watcher.DirectoryChangeEvent;

public class FileEvent {
    private FileEventEnum eventType;
    private Path eventPath;
    private Date eventDate;

    private FileEvent() {
        this.eventDate = new Date();
    }

    public FileEvent(FileEventEnum eventType, Path eventPath) {
        this.eventType = eventType;
        this.eventPath = eventPath;
    }

    public FileEvent(DirectoryChangeEvent event) {
        this.eventType = FileEventEnum.valueOf(event.eventType());
        this.eventPath = event.path();
    }

    public FileEventEnum getEventType() {
        return eventType;
    }

    public Path getEventPath() {
        return eventPath;
    }

    public Date getEventDate() {
        return this.eventDate;
    }
}
