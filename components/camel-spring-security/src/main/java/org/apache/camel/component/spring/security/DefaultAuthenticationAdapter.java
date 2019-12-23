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
package org.apache.camel.component.spring.security;

import java.util.Set;

import javax.security.auth.Subject;

import org.springframework.security.core.Authentication;

public class DefaultAuthenticationAdapter implements AuthenticationAdapter {

    @Override
    public Authentication toAuthentication(Subject subject) {
        if (subject == null || subject.getPrincipals().size() == 0) {
            return null;
        }
        Set<Authentication> authentications  = subject.getPrincipals(Authentication.class);
        if (authentications.size() > 0) {
            // just return the first one 
            return authentications.iterator().next();
        } else {
            return convertToAuthentication(subject);
        }
    }

    /**
     * You can add the customer convert code here
     */
    protected Authentication convertToAuthentication(Subject subject) {
        return null;        
    }

}
