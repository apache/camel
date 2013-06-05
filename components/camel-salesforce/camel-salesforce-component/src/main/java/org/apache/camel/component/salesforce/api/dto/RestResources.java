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
package org.apache.camel.component.salesforce.api.dto;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * DTO for Salesforce Resources.
 */
@XStreamAlias("urls")
public class RestResources extends AbstractDTOBase {

    private String sobjects;
    private String identity;
    private String connect;
    private String search;
    private String query;
    private String chatter;
    private String recent;
    private String tooling;

    public String getSobjects() {
        return sobjects;
    }

    public void setSobjects(String sobjects) {
        this.sobjects = sobjects;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getConnect() {
        return connect;
    }

    public void setConnect(String connect) {
        this.connect = connect;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getChatter() {
        return chatter;
    }

    public void setChatter(String chatter) {
        this.chatter = chatter;
    }

    public String getRecent() {
        return recent;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public String getTooling() {
        return tooling;
    }

    public void setTooling(String tooling) {
        this.tooling = tooling;
    }

}
