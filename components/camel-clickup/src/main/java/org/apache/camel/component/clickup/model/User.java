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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("id")
    private Long id; // example: 2478114,

    @JsonProperty("username")
    private String username; // example: "Nicolò Scarpa",

    @JsonProperty("email")
    private String email; // example: "nicolo.scarpa@blutec.it",

    @JsonProperty("color")
    private String color; // example: "#2ea52c",

    @JsonProperty("initials")
    private String initials; // example: "NS",

    @JsonProperty("profilePicture")
    private String profilePicture; // example: "https://attachments.clickup.com/profilePictures/2478114_RSb.jpg"

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getColor() {
        return color;
    }

    public String getInitials() {
        return initials;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    @Override
    public String toString() {
        return "User{" + "id="
                + id + ", username='"
                + username + '\'' + ", email='"
                + email + '\'' + ", color='"
                + color + '\'' + ", initials='"
                + initials + '\'' + ", profilePicture='"
                + profilePicture + '\'' + '}';
    }
}
