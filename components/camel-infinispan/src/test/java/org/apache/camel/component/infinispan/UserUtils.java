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
package org.apache.camel.component.infinispan;

import java.util.List;
import org.infinispan.protostream.sampledomain.User;

public final class UserUtils {
    public static final User[] USERS = new User[]{
            createUser("nameA", "surnameA"),
            createUser("nameA", "surnameB"),
            createUser("nameB", "surnameB")};

    private UserUtils() {
    }

    public static String createKey(User user) {
        return String.format("%s+%s", user.getName(), user.getSurname());
    }

    public static User createUser(String name, String surname) {
        User user = new User();
        user.setName(name);
        user.setSurname(surname);
        return user;
    }

    public static boolean eq(String str1, String str2) {
        if (str1 == null) {
            return str2 == null;
        } else {
            return str1.equals(str2);
        }
    }

    public static boolean eq(User user, String name, String surname) {
        if (user == null) {
            return false;
        }
        if (!eq(user.getName(), name)) {
            return false;
        }
        if (!eq(user.getSurname(), surname)) {
            return false;
        }
        return true;
    }

    public static boolean hasUser(List<User> users, String name, String surname) {
        if (users == null) {
            return false;
        }
        for (User user : users) {
            if (eq(user, name, surname)) {
                return true;
            }
        }
        return false;
    }
}
