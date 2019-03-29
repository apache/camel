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
package org.apache.camel.component.wordpress.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * A named status for the object.
 */
@JacksonXmlRootElement(localName = "publishableStatus")
public enum PublishableStatus {
    // @formatter:off
    publish, future, draft, pending, @JsonProperty("private")
    private_, trash, @JsonProperty("auto-draft")
    auto_draft, inherit, any;
    // @formatter:on

    /***
     * @param arg
     * @return
     * @see <a href= "https://stackoverflow.com/questions/33357594/java-enum-case-insensitive-jersey-query-param-binding">Java: Enum case insensitive Jersey Query Param Binding</a>
     */
    public static PublishableStatus fromString(String arg) {
        arg = "".concat(arg).toLowerCase();
        if (!arg.isEmpty() && arg.startsWith("private")) {
            return private_;
        }
        if (!arg.isEmpty() && arg.startsWith("auto")) {
            return auto_draft;
        }

        return valueOf(arg);
    }
}
