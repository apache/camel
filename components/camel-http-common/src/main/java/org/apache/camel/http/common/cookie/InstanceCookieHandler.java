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
package org.apache.camel.http.common.cookie;

import java.net.CookieManager;
import java.net.CookiePolicy;

import org.apache.camel.Exchange;

/**
 * This implementation of the
 * {@link org.apache.camel.http.common.cookie.CookieHandler} interface keeps the
 * cookies with the instance of this object. If it is shared between endpoints
 * the sessions will be shared as long as they are sent to the same domain.
 */
public class InstanceCookieHandler extends BaseCookieHandler {
    private CookieManager cookieHandler;

    @Override
    protected CookieManager getCookieManager(Exchange exchange) {
        return getCookieManagerInternal();
    }

    private CookieManager getCookieManagerInternal() {
        if (cookieHandler == null) {
            cookieHandler = new CookieManager();
        }
        return cookieHandler;
    }

    @Override
    public void setCookiePolicy(CookiePolicy cookiePolicy) {
        getCookieManagerInternal().setCookiePolicy(cookiePolicy);
    }
}
