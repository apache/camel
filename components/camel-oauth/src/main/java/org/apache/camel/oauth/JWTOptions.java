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
package org.apache.camel.oauth;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.json.JsonObject;

public class JWTOptions {
    private int leeway = 0;
    private boolean ignoreExpiration;
    private String algorithm = "HS256";
    private JsonObject header;
    private boolean noTimestamp;
    private int expires;
    private List<String> audience;
    private String issuer;
    private String subject;
    private List<String> permissions;
    private String nonceAlgorithm;

    public JWTOptions() {
        this.header = new JsonObject();
    }

    public int getLeeway() {
        return this.leeway;
    }

    public JWTOptions setLeeway(int leeway) {
        this.leeway = leeway;
        return this;
    }

    public boolean isIgnoreExpiration() {
        return this.ignoreExpiration;
    }

    public JWTOptions setIgnoreExpiration(boolean ignoreExpiration) {
        this.ignoreExpiration = ignoreExpiration;
        return this;
    }

    public String getAlgorithm() {
        return this.algorithm;
    }

    public JWTOptions setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public JsonObject getHeader() {
        return this.header;
    }

    public JWTOptions setHeader(JsonObject header) {
        this.header = header;
        return this;
    }

    public boolean isNoTimestamp() {
        return this.noTimestamp;
    }

    public JWTOptions setNoTimestamp(boolean noTimestamp) {
        this.noTimestamp = noTimestamp;
        return this;
    }

    public int getExpiresInSeconds() {
        return this.expires;
    }

    public JWTOptions setExpiresInSeconds(int expires) {
        this.expires = expires;
        return this;
    }

    public JWTOptions setExpiresInMinutes(int expiresInMinutes) {
        this.expires = expiresInMinutes * 60;
        return this;
    }

    public List<String> getAudience() {
        return this.audience;
    }

    public JWTOptions setAudience(List<String> audience) {
        this.audience = audience;
        return this;
    }

    public JWTOptions addAudience(String audience) {
        if (this.audience == null) {
            this.audience = new ArrayList<>();
        }

        this.audience.add(audience);
        return this;
    }

    public String getIssuer() {
        return this.issuer;
    }

    public JWTOptions setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public String getSubject() {
        return this.subject;
    }

    public JWTOptions setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getNonceAlgorithm() {
        return this.nonceAlgorithm;
    }

    public JWTOptions setNonceAlgorithm(String nonceAlgorithm) {
        this.nonceAlgorithm = nonceAlgorithm;
        return this;
    }
}
