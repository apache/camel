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

package org.apache.camel.component.clickup.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.camel.component.clickup.UnixTimestampDeserializer;
import org.apache.camel.component.clickup.UnixTimestampSerializer;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class HistoryItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("date")
    @JsonDeserialize(using = UnixTimestampDeserializer.class)
    @JsonSerialize(using = UnixTimestampSerializer.class)
    protected Instant date;

    @JsonProperty("field")
    protected String field;

    @JsonProperty("user")
    protected User user;

    public Instant getDate() {
        return date;
    }

    public String getField() {
        return field;
    }

    public User getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "HistoryItem{" + "date=" + date + ", field='" + field + '\'' + ", user=" + user + '}';
    }
}
