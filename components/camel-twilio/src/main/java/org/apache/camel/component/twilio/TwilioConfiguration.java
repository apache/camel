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
package org.apache.camel.component.twilio;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.twilio.internal.TwilioApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for Twilio component.
 */
@UriParams
public class TwilioConfiguration implements Cloneable {

    @UriPath
    @Metadata(required = "true")
    private TwilioApiName apiName;

    @UriPath(enums = "create,delete,fetch,read,update")
    @Metadata(required = "true")
    private String methodName;

    @UriParam(label = "common,security", secret = true)
    private String username;

    @UriParam(label = "common,security", secret = true)
    private String password;

    @UriParam(label = "common,security", secret = true)
    private String accountSid;

    /**
     * Returns a copy of this configuration
     */
    public TwilioConfiguration copy() {
        try {
            return (TwilioConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * What kind of operation to perform
     *
     * @return the API Name
     */
    public TwilioApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     *
     * @param apiName
     *            the API Name to set
     */
    public void setApiName(TwilioApiName apiName) {
        this.apiName = apiName;
    }

    /**
     * What sub operation to use for the selected operation
     *
     * @return the methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     *
     * @param methodName
     *            the methodName to set
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The account to use.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Auth token for the account.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getAccountSid() {
        return accountSid == null ? username : accountSid;
    }

    /**
     * The account SID to use.
     */
    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }
}
