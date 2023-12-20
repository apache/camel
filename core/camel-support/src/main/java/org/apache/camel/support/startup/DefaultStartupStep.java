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
package org.apache.camel.support.startup;

import org.apache.camel.StartupStep;

public class DefaultStartupStep implements StartupStep {

    private final String type;
    private final String name;
    private final String description;
    private final int id;
    private final int parentId;
    private final int level;
    private final long time;
    private long duration;

    public DefaultStartupStep(String type, String name, String description, int id, int parentId, int level, long time) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.id = id;
        this.parentId = parentId;
        this.level = level;
        this.time = time;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getParentId() {
        return parentId;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public long getBeginTime() {
        return time;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public void endStep() {
        this.duration = System.currentTimeMillis() - time;
    }

}
