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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.google.gdata.client.authn.oauth.OAuthHmacSha1Signer;
import com.google.gdata.client.authn.oauth.OAuthParameters;
import com.google.gdata.client.calendar.CalendarService;
import com.google.gdata.data.calendar.CalendarEntry;
import com.google.gdata.data.calendar.CalendarFeed;

/**
 * Facade for getting calendar names from the Google Calendar API. The access is made on
 * behalf of a user by providing an OAuth access token and access token secret.
 */
public class TutorialService {

    private Properties credentials;

    /**
     * Sets properties that contains the application's consumer key and consumer secret.
     *
     * @param credentials consumer key and consumer secret.
     */
    public void setCredentials(Properties credentials) {
        this.credentials = credentials;
    }

    /**
     * Obtains a list of names of a user's public and private calendars from the Google
     * Calendar API.
     * 
     * @param accessToken OAuth access token.
     * @param accessTokenSecret OAuth access token secret.
     * @return list of names of a user's public and private calendars.
     */
    public List<String> getCalendarNames(String accessToken, String accessTokenSecret) throws Exception {
        CalendarService calendarService = new CalendarService("apache-camel-2.3"); 
        OAuthParameters params = getOAuthParams(accessToken, accessTokenSecret);
        calendarService.setOAuthCredentials(params, new OAuthHmacSha1Signer());
        URL feedUrl = new URL("http://www.google.com/calendar/feeds/default/");
        CalendarFeed resultFeed = calendarService.getFeed(feedUrl, CalendarFeed.class);

        List<String> result = new ArrayList<String>();
        for (int i = 0; i < resultFeed.getEntries().size(); i++) {
            CalendarEntry entry = resultFeed.getEntries().get(i);
            result.add(entry.getTitle().getPlainText());
        }
        return result;
    }
    
    private OAuthParameters getOAuthParams(String accessToken, String accessTokenSecret) {
        OAuthParameters params = new OAuthParameters();
        params.setOAuthConsumerKey(credentials.getProperty("consumer.key"));
        params.setOAuthConsumerSecret(credentials.getProperty("consumer.secret"));
        params.setOAuthToken(accessToken);
        params.setOAuthTokenSecret(accessTokenSecret);
        return params;
    }
    
}
