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
package org.apache.camel.component.netty.http;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

/**
 * A {@link SecurityAuthenticator} allows to plugin custom authenticators,
 * such as JAAS based or custom implementations.
 */
public interface SecurityAuthenticator {

    /**
     * Sets the name of the realm to use.
     */
    void setName(String name);

    /**
     * Gets the name of the realm.
     */
    String getName();

    /**
     * Sets the role class names (separated by comma)
     * <p/>
     * By default if no explicit role class names has been configured, then this implementation
     * will assume the {@link Subject} {@link java.security.Principal}s is a role if the classname
     * contains the word <tt>role</tt> (lower cased).
     *
     * @param names a list of FQN class names for role {@link java.security.Principal} implementations.
     */
    void setRoleClassNames(String names);

    /**
     * Attempts to login the {@link java.security.Principal} on this realm.
     * <p/>
     * The login is a success if no Exception is thrown, and a {@link Subject} is returned.
     *
     * @param principal       the principal
     * @return the subject for the logged in principal, must <b>not</b> be <tt>null</tt>
     * @throws LoginException is thrown if error logging in the {@link java.security.Principal}
     */
    Subject login(HttpPrincipal principal) throws LoginException;

    /**
     * Attempt to logout the subject.
     *
     * @param subject  subject to logout
     * @throws LoginException is thrown if error logging out subject
     */
    void logout(Subject subject) throws LoginException;

    /**
     * Gets the user roles from the given {@link Subject}
     *
     * @param subject the subject
     * @return <tt>null</tt> if no roles, otherwise a String with roles separated by comma.
     */
    String getUserRoles(Subject subject);

}
