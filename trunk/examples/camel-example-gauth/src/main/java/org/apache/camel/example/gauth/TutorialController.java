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


import java.util.List;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gdata.util.AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Single controller for the demo application that handles GET requests. Obtains OAuth access
 * token and access token secret from cookies and uses them to obtain calendar names from the
 * Google Calendar API. If the interaction with the calendar API fails due to invalid or non-
 * existing OAuth tokens an error message is displayed in authorize.jsp. If it succeeds the
 * calendar names are displayed in calendar.jsp.
 * <p>
 * In production systems it is <em>not</em> recommended to store access tokens in cookies. The
 * recommended approach is to store them in a database. The demo application is only doing that
 * to keep the example as simple as possible. However, an attacker could not use an access token
 * alone to get access to a user's calendar data because the application's consumer secret is
 * necessary for that as well. The consumer secret never leaves the demo application.
 */
@Controller
@RequestMapping("/calendar")
public class TutorialController {

    @Autowired
    private TutorialService service;
    
    @RequestMapping(method = RequestMethod.GET)
    public String handleGet(
            HttpServletRequest request, 
            HttpServletResponse response, 
            ModelMap model) throws Exception {

        List<String> calendarNames = null;

        // Get OAuth tokens from cookies
        String accessToken = getAccessToken(request);
        String accessTokenSecret = getAccessTokenSecret(request);
        
        if (accessToken == null) {
            model.put("message", "No OAuth access token available");
            return "/WEB-INF/jsp/authorize.jsp";
        }
        
        try {
            // Get calendar names from Google Calendar API
            calendarNames = service.getCalendarNames(accessToken, accessTokenSecret);
        } catch (AuthenticationException e) {
            model.put("message", "OAuth access token invalid");
            return "/WEB-INF/jsp/authorize.jsp";
        }
        
        model.put("calendarNames", calendarNames);
        return "/WEB-INF/jsp/calendar.jsp";        
    }
    
    private static String getAccessToken(HttpServletRequest request) {
        return getCookieValue(request.getCookies(), "TUTORIAL-ACCESS-TOKEN");
    }
    
    private static String getAccessTokenSecret(HttpServletRequest request) {
        return getCookieValue(request.getCookies(), "TUTORIAL-ACCESS-TOKEN-SECRET");
    }
    
    private static String getCookieValue(Cookie[] cookies, String name) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

}
