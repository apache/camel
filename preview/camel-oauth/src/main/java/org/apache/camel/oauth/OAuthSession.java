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

import java.util.Optional;

public interface OAuthSession {

    String getSessionId();

    <T> Optional<T> getValue(String key, Class<T> clazz);

    <T> void putValue(String key, T value);

    <T> Optional<T> removeValue(String key);

    default Optional<UserProfile> getUserProfile() {
        return getValue(UserProfile.class.getName(), UserProfile.class);
    }

    default void putUserProfile(UserProfile profile) {
        putValue(UserProfile.class.getName(), profile);
    }

    default Optional<UserProfile> removeUserProfile() {
        return removeValue(UserProfile.class.getName());
    }
}
