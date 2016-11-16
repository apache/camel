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
package org.apache.camel.component.springldap;

import org.springframework.ldap.core.LdapOperations;

/**
 * Provides a way to invoke any method on {@link LdapOperations} when an
 * operation is not provided out of the box by this component.
 * 
 * @param <Q> - The set of request parameters as expected by the method being
 *            invoked
 * @param <S> - The response to be returned by the method being invoked
 */
public interface LdapOperationsFunction<Q, S> {

    /**
     * @param ldapOperations - An instance of {@link LdapOperations}
     * @param request - Any object needed by the {@link LdapOperations} method
     *            being invoked
     * @return - result of the {@link LdapOperations} method being invoked
     */
    S apply(LdapOperations ldapOperations, Q request);

}
