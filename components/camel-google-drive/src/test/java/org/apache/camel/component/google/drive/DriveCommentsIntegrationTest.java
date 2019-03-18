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
package org.apache.camel.component.google.drive;

import java.util.HashMap;
import java.util.Map;

import com.google.api.services.drive.model.Comment;
import com.google.api.services.drive.model.File;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.drive.internal.DriveCommentsApiMethod;
import org.apache.camel.component.google.drive.internal.DriveFilesApiMethod;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for com.google.api.services.drive.Drive$Comments APIs.
 */
public class DriveCommentsIntegrationTest extends AbstractGoogleDriveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DriveCommentsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleDriveApiCollection.getCollection().getApiName(DriveCommentsApiMethod.class).getName();
    
    @Test
    public void testComment() throws Exception {
        // 1. create test file
        File testFile = uploadTestFile();
        String fileId = testFile.getId();
        
        // 2. comment on that file
        Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", fileId);
        // parameter type is com.google.api.services.drive.model.Comment
        com.google.api.services.drive.model.Comment comment = new com.google.api.services.drive.model.Comment();
        comment.setContent("Camel rocks!");
        headers.put("CamelGoogleDrive.content", comment);

        requestBodyAndHeaders("direct://INSERT", null, headers);

        // 3. get a list of comments on the file
        // using String message body for single parameter "fileId"
        com.google.api.services.drive.model.CommentList result1 = requestBody("direct://LIST", fileId);

        assertNotNull(result1.get("items"));
        LOG.debug("list: " + result1);
        
        Comment comment2 = result1.getItems().get(0);
        
        // 4. now try and get that comment 
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", fileId);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", comment2.getCommentId());

        final com.google.api.services.drive.model.Comment result3 = requestBodyAndHeaders("direct://GET", null, headers);

        assertNotNull("get result", result3);
        
        // 5. delete the comment
        
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", fileId);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", comment2.getCommentId());

        requestBodyAndHeaders("direct://DELETE", null, headers);

        // 6. ensure the comment is gone
        
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleDrive.fileId", fileId);
        // parameter type is String
        headers.put("CamelGoogleDrive.commentId", comment2.getCommentId());

        try {
            final com.google.api.services.drive.model.Comment result4 = requestBodyAndHeaders("direct://GET", null, headers);
            assertTrue("Should have thrown an exception.", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for delete
                from("direct://DELETE")
                    .to("google-drive://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET")
                    .to("google-drive://" + PATH_PREFIX + "/get");

                // test route for insert
                from("direct://INSERT")
                    .to("google-drive://" + PATH_PREFIX + "/insert");

                // test route for list
                from("direct://LIST")
                    .to("google-drive://" + PATH_PREFIX + "/list?inBody=fileId");

                // test route for patch
                from("direct://PATCH")
                    .to("google-drive://" + PATH_PREFIX + "/patch");

                // test route for update
                from("direct://UPDATE")
                    .to("google-drive://" + PATH_PREFIX + "/update");
                
                // just used to upload file for test
                from("direct://INSERT_1")
                    .to("google-drive://" + GoogleDriveApiCollection.getCollection().getApiName(DriveFilesApiMethod.class).getName() + "/insert");

            }
        };
    }
}
