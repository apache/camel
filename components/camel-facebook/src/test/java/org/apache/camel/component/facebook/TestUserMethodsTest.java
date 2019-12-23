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
package org.apache.camel.component.facebook;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import facebook4j.TestUser;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Test methods in {@link facebook4j.api.TestUserMethods}
 */
public class TestUserMethodsTest extends CamelFacebookTestSupport {

    private static final String TEST_USER1 = "test one";
    private static final String TEST_USER2 = "test two";

    public TestUserMethodsTest() throws Exception {
    }

    @Test
    public void testTestUsers() {

        // create a test user with exchange properties
        final TestUser testUser1 = template().requestBody("direct:createTestUser", TEST_USER1, TestUser.class);
        assertNotNull("Test User1", testUser1);

        // create a test user with exchange properties
        final TestUser testUser2 = template().requestBody("direct:createTestUser", TEST_USER2, TestUser.class);
        assertNotNull("Test User2", testUser2);

        // make friends, not enemies
        final Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFacebook.testUser2", testUser2);
        Boolean worked = template().requestBodyAndHeaders("direct:makeFriendTestUser", testUser1, headers, Boolean.class);
        assertTrue("Friends not made", worked);

        // get app test users
        final List testUsers = template().requestBody("direct:testUsers", null, List.class);
        assertNotNull("Test users", testUsers);
        assertFalse("Empty test user list", testUsers.isEmpty());

        // delete test users
        for (Object user : testUsers) {
            final TestUser testUser = (TestUser) user;
            if (testUser.equals(testUser1) || testUser.equals(testUser2)) {
                final String id = testUser.getId();
                worked = template().requestBody("direct:deleteTestUser", id, Boolean.class);
                assertTrue("Test user not deleted for id " + id, worked);
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createTestUser")
                    .to("facebook://createTestUser?inBody=name&appId=" + properties.get("oAuthAppId")
                        + "&userLocale=" + Locale.getDefault().toString()
                        + "&permissions=" + getTestPermissions()
                        + "&" + getAppOauthParams());

                // note short form testUsers instead of getTestUsers
                from("direct:testUsers")
                    .to("facebook://testUsers?appId=" + properties.get("oAuthAppId") + "&" + getAppOauthParams());

                from("direct:makeFriendTestUser")
                    .to("facebook://makeFriendTestUser?inBody=testUser1&" + getAppOauthParams());

                from("direct:deleteTestUser")
                    .to("facebook://deleteTestUser?inBody=testUserId&" + getAppOauthParams());
            }
        };
    }

    public String getTestPermissions() {
        return "email"
            + ",publish_actions"
            + ",user_about_me"
            + ",user_activities"
            + ",user_birthday"
            + ",user_checkins"
            + ",user_education_history"
            + ",user_events"
            + ",user_games_activity"
            + ",user_groups"
            + ",user_hometown"
            + ",user_interests"
            + ",user_likes"
            + ",user_location"
            + ",user_notes"
            + ",user_photos"
            + ",user_questions"
            + ",user_relationship_details"
            + ",user_relationships"
            + ",user_religion_politics"
            + ",user_status"
            + ",user_subscriptions"
            + ",user_videos"
            + ",user_website"
            + ",user_work_history"
            + ",friends_about_me"
            + ",friends_activities"
            + ",friends_birthday"
            + ",friends_checkins"
            + ",friends_education_history"
            + ",friends_events"
            + ",friends_games_activity"
            + ",friends_groups"
            + ",friends_hometown"
            + ",friends_interests"
            + ",friends_likes"
            + ",friends_location"
            + ",friends_notes"
            + ",friends_photos"
            + ",friends_questions"
            + ",friends_relationship_details"
            + ",friends_relationships"
            + ",friends_religion_politics"
            + ",friends_status"
            + ",friends_subscriptions"
            + ",friends_videos"
            + ",friends_website"
            + ",friends_work_history"
            + ",ads_management"
            + ",create_event"
            + ",create_note"
            + ",export_stream"
            + ",friends_online_presence"
            + ",manage_friendlists"
            + ",manage_notifications"
            + ",manage_pages"
            + ",photo_upload"
            + ",publish_checkins"
            + ",publish_stream"
            + ",read_friendlists"
            + ",read_insights"
            + ",read_mailbox"
            + ",read_page_mailboxes"
            + ",read_requests"
            + ",read_stream"
            + ",rsvp_event"
            + ",share_item"
            + ",sms"
            + ",status_update"
            + ",user_online_presence"
            + ",video_upload"
            + ",xmpp_login";
    }

}
