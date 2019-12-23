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

import java.io.FileNotFoundException;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for
 * {@link BoxCommentsManager} APIs.
 */
public class BoxCommentsManagerIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxCommentsManagerIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxCommentsManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_COMMENT = "CamelTestFile comment.";
    private static final String CAMEL_TEST_FILE_CHANGED_COMMENT = "CamelTestFile changed comment.";
    private static final String CAMEL_TEST_FILE_REPLY_COMMENT = "CamelTestFile changed comment.";

    @Test
    public void testAddFileComment() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.fileId", testFile.getID());
        // parameter type is String
        headers.put("CamelBox.message", CAMEL_TEST_FILE_COMMENT);

        final com.box.sdk.BoxFile result = requestBodyAndHeaders("direct://ADDFILECOMMENT", null, headers);

        assertNotNull("addFileComment result", result);
        assertNotNull("addFileComment comments", result.getComments());
        assertTrue("changeCommentMessage comments size", result.getComments().size() > 0);
        assertEquals("changeCommentMessage comment message", CAMEL_TEST_FILE_COMMENT,
                result.getComments().get(0).getMessage());
        LOG.debug("addFileComment: " + result);
    }

    @Test
    public void testChangeCommentMessage() throws Exception {

        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.commentId", commentInfo.getID());
        // parameter type is String
        headers.put("CamelBox.message", CAMEL_TEST_FILE_CHANGED_COMMENT);

        final com.box.sdk.BoxComment result = requestBodyAndHeaders("direct://CHANGECOMMENTMESSAGE", null, headers);

        assertNotNull("changeCommentMessage result", result);
        assertNotNull("changeCommentMessage message", result.getInfo().getMessage());
        assertEquals("changeCommentMessage message", CAMEL_TEST_FILE_CHANGED_COMMENT, result.getInfo().getMessage());
        LOG.debug("changeCommentMessage: " + result);
    }

    @Test
    public void testDeleteComment() throws Exception {
        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        // using String message body for single parameter "commentId"
        requestBody("direct://DELETECOMMENT", commentInfo.getID());

        List<BoxComment.Info> comments = testFile.getComments();
        assertNotNull("deleteComment comments", comments);
        assertEquals("deleteComment comments empty", 0, comments.size());
    }

    @Test
    public void testGetCommentInfo() throws Exception {

        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        // using String message body for single parameter "commentId"
        final com.box.sdk.BoxComment.Info result = requestBody("direct://GETCOMMENTINFO", commentInfo.getID());

        assertNotNull("getCommentInfo result", result);
        assertEquals("getCommentInfo message", CAMEL_TEST_FILE_COMMENT, result.getMessage());
        LOG.debug("getCommentInfo: " + result);
    }

    @Test
    public void testGetFileComments() throws Exception {
        testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        // using String message body for single parameter "fileId"
        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBody("direct://GETFILECOMMENTS", testFile.getID());

        assertNotNull("getFileComments result", result);
        assertEquals("getFileComments size", 1, result.size());
        LOG.debug("getFileComments: " + result);
    }

    @Test
    public void testReplyToComment() throws Exception {

        BoxComment.Info commentInfo = testFile.addComment(CAMEL_TEST_FILE_COMMENT);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.commentId", commentInfo.getID());
        // parameter type is String
        headers.put("CamelBox.message", CAMEL_TEST_FILE_REPLY_COMMENT);

        final com.box.sdk.BoxComment result = requestBodyAndHeaders("direct://REPLYTOCOMMENT", null, headers);

        assertNotNull("replyToComment result", result);
        assertEquals("replyToComment result", CAMEL_TEST_FILE_REPLY_COMMENT, result.getInfo().getMessage());
        LOG.debug("replyToComment: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
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

    @Before
    public void setupTest() throws Exception {
        createTestFile();
    }

    @After
    public void teardownTest() {
        deleteTestFile();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/addFileComment");
        return endpoint.getBoxConnection();
    }

    private void createTestFile() throws FileNotFoundException {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
    }

}
