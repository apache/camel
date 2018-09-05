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
package org.apache.camel.example.rest;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link org.apache.camel.example.rest.User} service which we rest enable from the {@link org.apache.camel.example.rest.UserRouteBuilder}.
 */
public class UserService {

    // use a tree map so they become sorted
    private final Map<String, User> users = new TreeMap<>();

    public UserService() {
        users.put("123", new User(123, "John Doe"));
        users.put("456", new User(456, "Donald Duck"));
    }

    /**
     * Gets a user by the given id
     *
     * @param id  the id of the user
     * @return the user, or <tt>null</tt> if no user exists
     */
    public User getUser(String id) {
        return users.get(id);
    }

    /**
     * List all users
     *
     * @return the list of all users
     */
    public Collection<User> listUsers() {
        return users.values();
    }

    /**
     * Updates or creates the given user
     *
     * @param user the user
     */
    public void updateUser(User user) {
        users.put("" + user.getId(), user);
    }
}
