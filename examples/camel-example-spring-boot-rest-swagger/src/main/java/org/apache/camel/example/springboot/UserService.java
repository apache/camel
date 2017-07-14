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
package org.apache.camel.example.springboot;

import java.util.Collection;

/**
 * Service interface for managing users.
 * 
 */
public interface UserService {

    /**
     * Find a user by the given ID
     *
     * @param id
     *            the ID of the user
     * @return the user, or <code>null</code> if user not found.
     */
    User findUser(Integer id);

    /**
     * Find all users
     *
     * @return a collection of all users
     */
    Collection<User> findUsers();

    /**
     * Update the given user
     *
     * @param user
     *            the user
     */
    void updateUser(User user);

}
