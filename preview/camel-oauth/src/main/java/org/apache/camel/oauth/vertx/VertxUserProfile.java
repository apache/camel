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
package org.apache.camel.oauth.vertx;

import java.text.ParseException;
import java.util.Map;

import com.nimbusds.jose.util.JSONObjectUtils;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.apache.camel.oauth.UserProfile;

public class VertxUserProfile extends UserProfile {

    private final User vtxUser;

    public VertxUserProfile(User vtxUser) {
        super(deepMap(vtxUser.attributes()), deepMap(vtxUser.principal()));
        this.vtxUser = vtxUser;
    }

    public User getVertxUser() {
        return this.vtxUser;
    }

    private static Map<String, Object> deepMap(JsonObject obj) {
        try {
            return JSONObjectUtils.parse(obj.encode());
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

}
