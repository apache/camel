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
package org.apache.camel.component.box;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxComment;
import com.box.sdk.BoxFolder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxCommentsManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxCommentsManagerApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link BoxCommentsManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxCommentsManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxCommentsManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxCommentsManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_COMMENT = "CamelTestFile comment.";
    private static final String CAMEL_TEST_FILE_CHANGED_COMMENT = "CamelTestFile changed comment.";
    private static final String CAMEL_TEST_FILE_REPLY_COMMENT = "CamelTestFile changed comment.";

    @Test
    public void testAddFileComment() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is String
        headers.put("CamelBox.message", CAMEL_TEST_FILE_COMMENT);

        final com.box.sdk.BoxFile result = requestBodyAndHeaders("direct://ADDFILECOMMENT", null, headers);

        assertNotNull(result, "addFileComment result");
        assertNotNull(result.getComments(), "addFileComment comments");
        assertTrue(result.getComments().size() > 0, "changeCommentMessage comments size");
        assertEquals(CAMEL_TEST_FILE_COMMENT, result.getComments().get(0).getMessage(), "changeCommentMessage comment message");
        LOG.debug("addFileComment: {}", result);
    }

    @Test
    public void testChangeCommentMessage() {

        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.commentId", commentInfo.getID());
        // parameter type is String
        headers.put("CamelBox.message", CAMEL_TEST_FILE_CHANGED_COMMENT);

        final com.box.sdk.BoxComment result = requestBodyAndHeaders("direct://CHANGECOMMENTMESSAGE", null, headers);

        assertNotNull(result, "changeCommentMessage result");
        assertNotNull(result.getInfo().getMessage(), "changeCommentMessage message");
        assertEquals(CAMEL_TEST_FILE_CHANGED_COMMENT, result.getInfo().getMessage(), "changeCommentMessage message");
        LOG.debug("changeCommentMessage: {}", result);
    }

    @Test
    public void testDeleteComment() {
        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        // using String message body for single parameter "commentId"
        requestBody("direct://DELETECOMMENT", commentInfo.getID());

        List<BoxComment.Info> comments = testFile.getComments();
        assertNotNull(comments, "deleteComment comments");
        assertEquals(0, comments.size(), "deleteComment comments empty");
    }

    @Test
    public void testGetCommentInfo() {

        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        // using String message body for single parameter "commentId"
        final com.box.sdk.BoxComment.Info result = requestBody("direct://GETCOMMENTINFO", commentInfo.getID());

        assertNotNull(result, "getCommentInfo result");
        assertEquals(CAMEL_TEST_FILE_COMMENT, result.getMessage(), "getCommentInfo message");
        LOG.debug("getCommentInfo: {}", result);
    }

    @Test
    public void testGetFileComments() {
        testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        // using String message body for single parameter "fileId"
        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBody("direct://GETFILECOMMENTS", testFile.getID());

        assertNotNull(result, "getFileComments result");
        assertEquals(1, result.size(), "getFileComments size");
        LOG.debug("getFileComments: {}", result);
    }

    @Test
    public void testReplyToComment() {

        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.commentId", commentInfo.getID());
        // parameter type is String
        headers.put("CamelBox.message", CAMEL_TEST_FILE_REPLY_COMMENT);

        final com.box.sdk.BoxComment result = requestBodyAndHeaders("direct://REPLYTOCOMMENT", null, headers);

        assertNotNull(result, "replyToComment result");
        assertEquals(CAMEL_TEST_FILE_REPLY_COMMENT, result.getInfo().getMessage(), "replyToComment result");
        LOG.debug("replyToComment: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for addFileComment
                from("direct://ADDFILECOMMENT").to("box://" + PATH_PREFIX + "/addFileComment");

                // test route for changeCommentMessage
                from("direct://CHANGECOMMENTMESSAGE").to("box://" + PATH_PREFIX + "/changeCommentMessage");

                // test route for deleteComment
                from("direct://DELETECOMMENT").to("box://" + PATH_PREFIX + "/deleteComment?inBody=commentId");

                // test route for getCommentInfo
                from("direct://GETCOMMENTINFO").to("box://" + PATH_PREFIX + "/getCommentInfo?inBody=commentId");

                // test route for getFileComments
                from("direct://GETFILECOMMENTS").to("box://" + PATH_PREFIX + "/getFileComments?inBody=fileId");

                // test route for replyToComment
                from("direct://REPLYTOCOMMENT").to("box://" + PATH_PREFIX + "/replyToComment");

            }
        };
    }

    @BeforeEach
    public void setupTest() throws Exception {
        createTestFile();
    }

    @AfterEach
    public void teardownTest() {
        deleteTestFile();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/addFileComment");
        return endpoint.getBoxConnection();
    }

    private void createTestFile() {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
    }

}
