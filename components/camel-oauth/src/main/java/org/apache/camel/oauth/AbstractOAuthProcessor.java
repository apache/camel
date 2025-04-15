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

import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOAuthProcessor implements Processor {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String procName = getClass().getSimpleName();

    protected Optional<OAuth> findOAuth(CamelContext context) {
        return OAuthFactory.lookupFactory(context).findOAuth();
    }

    protected OAuth findOAuthOrThrow(CamelContext context) {
        return findOAuth(context).orElseThrow(() -> new NoSuchElementException("No OAuth"));
    }

    protected void authenticateExistingUserProfile(OAuth oauth, OAuthSession session) {
        // Remove before attempting to re-authenticate
        var userProfile = session.removeUserProfile().orElseThrow();
        if (userProfile.expired()) {
            var creds = new UserCredentials(userProfile);
            userProfile = oauth.authenticate(creds);
            userProfile.logDetails("Refreshed");
        } else {
            var creds = new TokenCredentials(userProfile.accessToken().orElseThrow());
            var updProfile = oauth.authenticate(creds);
            userProfile.merge(updProfile);
            userProfile.logDetails("ReAuthenticated");
        }
        session.putUserProfile(userProfile);
    }

    protected void logRequestHeaders(String msgPrefix, Message msg) {
        log.debug("{} - Request headers ...", msgPrefix);
        msg.getHeaders().forEach((k, v) -> {
            log.debug("   {}: {}", k, v);
        });
    }

    protected void sendRedirect(Message msg, String redirectUrl) {
        log.debug("Redirect to: {}", redirectUrl);
        msg.setHeader(Exchange.HTTP_RESPONSE_CODE, 302);
        msg.setHeader("Location", redirectUrl);
        msg.setBody("");
    }
}
