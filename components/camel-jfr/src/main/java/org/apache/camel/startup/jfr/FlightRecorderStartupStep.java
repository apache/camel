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
package org.apache.camel.startup.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import org.apache.camel.StartupStep;

@Name(FlightRecorderStartupStep.NAME)
@Category("Camel Application")
@Label("Startup Step")
@Description("Camel Application Startup")
@StackTrace(false)
public class FlightRecorderStartupStep extends Event implements StartupStep {

    public static final String NAME = "org.apache.camel.spi.CamelEvent";

    @Label("Event Source")
    public final String name;
    @Label("Event Id")
    public final int id;
    @Label("Event Parent Id")
    public final int parentId;
    @Label("Event Depth")
    public final int depth;
    @Label("Event Type")
    public final String type;
    @Label("Event Description")
    public final String description;

    public FlightRecorderStartupStep(String name, int id, int parentId, int depth, String type, String description) {
        this.name = name;
        this.id = id;
        this.parentId = parentId;
        this.depth = depth;
        this.type = type;
        this.description = description;
        begin();
    }

    @Override
    public String getName() {
        return name;
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
        return depth;
    }

    @Override
    public long getBeginTime() {
        // not used by jfr
        return 0;
    }

    @Override
    public long getDuration() {
        // not used by jfr
        return 0;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void endStep() {
        end();
        commit();
    }
}
