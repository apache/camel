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
package org.apache.camel.component.cron.api;

import java.util.StringJoiner;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CamelCronConfiguration {

    @UriPath
    @Metadata(required = true)
    private String name;

    @UriParam
    @Metadata(required = true)
    private String schedule;

    public String getName() {
        return name;
    }

    /**
     * The name of the cron trigger
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getSchedule() {
        return schedule;
    }

    /**
     * A cron expression that will be used to generate events
     */
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CamelCronConfiguration.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("schedule='" + schedule + "'")
                .toString();
    }
}
