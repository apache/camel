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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an incoming inline query.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequery">https://core.telegram.org/bots/api#inlinequery</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingInlineQuery {

    private String id;

    private User from;

    private Location location;

    private String query;

    private String offset;

    public IncomingInlineQuery() {
    }

    /**
     * @param id       Unique identifier for this query
     * @param from     Sender
     * @param location Optional. Sender location, only for bots that request user location
     * @param query    Text of the query (up to 512 characters)
     * @param offset   Offset of the results to be returned, can be controlled by the bot
     */
    public IncomingInlineQuery(String id, User from, Location location, String query, String offset) {
        this.id = id;
        this.from = from;
        this.location = location;
        this.query = query;
        this.offset = offset;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getFrom() {
        return from;
    }

    public void setFrom(User from) {
        this.from = from;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InlineQuery{");
        sb.append("id='").append(id).append('\'');
        sb.append(", from=").append(from);
        sb.append(", location=").append(location);
        sb.append(", query='").append(query).append('\'');
        sb.append(", offset='").append(offset).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
