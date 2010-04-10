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
package org.apache.camel.example.gauth;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static org.apache.camel.component.gae.auth.GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN;
import static org.apache.camel.component.gae.auth.GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN_SECRET;

/**
 * Reads an OAuth access token plus access token secret from a Camel message and stores them in
 * cookies. These cookies are needed by {@link org.apache.camel.example.gauth.TutorialController}
 * for accessing a user's calendar via the Google Calendar API. The cookies are valid for one
 * hour. Finally, it generates an HTTP 302 response that redirects the user to the application's
 * main location (/oauth/calendar).
 * <p>
 * In production systems it is <em>not</em> recommended to store access tokens in cookies. The 
 * recommended approach is to store them in a database. The demo application is only doing that
 * to keep the example as simple as possible. However, an attacker could not use an access token
 * alone to get access to a user's calendar data because the application's consumer secret is
 * necessary for that as well. The consumer secret never leaves the demo application.
 */
public class TutorialTokenProcessor implements Processor {

    private static final int ONE_HOUR = 3600;
    
    public void process(Exchange exchange) throws Exception {
        String accessToken = exchange.getIn().getHeader(GAUTH_ACCESS_TOKEN, String.class);
        String accessTokenSecret = exchange.getIn().getHeader(GAUTH_ACCESS_TOKEN_SECRET, String.class);
    
        if (accessToken != null) {
            HttpServletResponse servletResponse = exchange.getIn().getHeader(
                    Exchange.HTTP_SERVLET_RESPONSE, HttpServletResponse.class);
            
            Cookie accessTokenCookie = new Cookie("TUTORIAL-ACCESS-TOKEN", accessToken);
            Cookie accessTokenSecretCookie = new Cookie("TUTORIAL-ACCESS-TOKEN-SECRET", accessTokenSecret); 
            
            accessTokenCookie.setPath("/oauth/");
            accessTokenCookie.setMaxAge(ONE_HOUR);
            
            accessTokenSecretCookie.setPath("/oauth/");
            accessTokenSecretCookie.setMaxAge(ONE_HOUR);
            
            servletResponse.addCookie(accessTokenCookie);
            servletResponse.addCookie(accessTokenSecretCookie);
        }
        
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 302);
        exchange.getOut().setHeader("Location", "/oauth/calendar");
    }

}
