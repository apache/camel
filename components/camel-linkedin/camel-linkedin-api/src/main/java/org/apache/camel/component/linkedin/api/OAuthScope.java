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
package org.apache.camel.component.linkedin.api;

/**
 * OAuth scope for use in {@link LinkedInOAuthRequestFilter}
 */
public enum OAuthScope {
    
    R_BASICPROFILE("r_basicprofile"),
    R_FULLPROFILE("r_fullprofile"),
    R_EMAILADDRESS("r_emailaddress"),
    R_NETWORK("r_network"),
    R_CONTACTINFO("r_contactinfo"),
    @Deprecated // use W_SHARE instead
    RW_NUS("rw_nus"),
    RW_COMPANY_ADMIN("rw_company_admin"),
    RW_GROUPS("rw_groups"),
    W_MESSAGES("w_messages"),
    W_SHARE("w_share");

    private final String value;

    OAuthScope(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OAuthScope fromValue(String value) {
        for (OAuthScope scope : values()) {
            if (scope.value.equals(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException(value);
    }

    public static OAuthScope[] fromValues(String... values) {
        if (values == null || values.length == 0) {
            return new OAuthScope[0];
        }
        final OAuthScope[] result = new OAuthScope[values.length];
        int i = 0;
        for (String value : values) {
            result[i++] = fromValue(value);
        }
        return result;
    }
}
