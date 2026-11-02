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
package org.apache.camel.component.huaweicloud.iam;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.sdk.iam.v3.model.KeystoneGroupResult;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersResult;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupOption;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupResponse;
import com.huaweicloud.sdk.iam.v3.model.ShowUserRequest;
import com.huaweicloud.sdk.iam.v3.model.ShowUserResponse;
import com.huaweicloud.sdk.iam.v3.model.ShowUserResult;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserOption;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserRequest;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request of updateUser and updateGroup as JSON text, bytes, a stream or a file, and the field names of the JSON
 * results.
 */
class IAMProducerJsonTest extends CamelTestSupport {

    private static final String USER_JSON = "{\"name\":\"User 19\",\"description\":\"Zürich ✓\",\"pwd_status\":true}";
    private static final String GROUP_JSON = "{\"name\":\"Group 43\",\"description\":\"Zürich ✓\",\"domain_id\":\"123\"}";

    private final ObjectMapper mapper = new ObjectMapper();

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("iamClient")
    CapturingClient client = new CapturingClient();

    @TempDir
    Path tempDir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        String options = "accessKey=" + testConfiguration.getProperty("accessKey") +
                         "&secretKey=" + testConfiguration.getProperty("secretKey") +
                         "&region=" + testConfiguration.getProperty("region") +
                         "&ignoreSslVerification=true" +
                         "&iamClient=#iamClient";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:updateUser")
                        .to("hwcloud-iam:updateUser?userId=" + testConfiguration.getProperty("userId") + "&" + options);
                from("direct:updateGroup")
                        .to("hwcloud-iam:updateGroup?groupId=" + testConfiguration.getProperty("groupId") + "&" + options);
                from("direct:getUser")
                        .to("hwcloud-iam:getUser?userId=" + testConfiguration.getProperty("userId") + "&" + options);
                from("direct:listUsers")
                        .to("hwcloud-iam:listUsers?" + options);
                from("direct:listGroups")
                        .to("hwcloud-iam:listGroups?" + options);
            }
        };
    }

    @Test
    void updateUserReadsJsonBytes() {
        assertUserOption(updateUser(USER_JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void updateUserReadsJsonStream() {
        assertUserOption(updateUser(new ByteArrayInputStream(USER_JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void updateUserReadsJsonFile() throws Exception {
        File file = Files.writeString(tempDir.resolve("user.json"), USER_JSON, StandardCharsets.UTF_8).toFile();

        assertUserOption(updateUser(file));
    }

    @Test
    void updateUserFailsOnText() {
        Exchange exchange = send("direct:updateUser", "{\"name\":\"User 19\",\"password\":secret-value}");

        Exception failure = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertFalse(failure.getMessage().contains("secret"), failure.getMessage());
        assertNull(client.userRequest);
    }

    @Test
    void updateUserFailsOnBytesThatAreNotJson() {
        Exchange exchange = send("direct:updateUser", new byte[] { (byte) 0xff, (byte) 0xfe, 'x' });

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertNull(client.userRequest);
    }

    @Test
    void updateUserFailsWithoutBody() {
        Exchange exchange = send("direct:updateUser", null);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertNull(client.userRequest);
    }

    @Test
    void updateGroupReadsJsonBytes() {
        assertGroupOption(updateGroup(GROUP_JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void updateGroupReadsJsonStream() {
        assertGroupOption(updateGroup(new ByteArrayInputStream(GROUP_JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void updateGroupReadsJsonFile() throws Exception {
        File file = Files.writeString(tempDir.resolve("group.json"), GROUP_JSON, StandardCharsets.UTF_8).toFile();

        assertGroupOption(updateGroup(file));
    }

    @Test
    void updateGroupFailsOnUnknownField() {
        Exchange exchange = send("direct:updateGroup", "{\"name\":\"Group 43\",\"domainId\":\"123\"}");

        Exception failure = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(failure.getMessage().contains("(unknown field domainId at line 1, column "), failure.getMessage());
        assertNull(client.groupRequest);
    }

    @Test
    void updateGroupFailsWithoutBody() {
        Exchange exchange = send("direct:updateGroup", null);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertNull(client.groupRequest);
    }

    @Test
    void getUserGivesApiFieldNames() throws Exception {
        Map<String, Object> user = mapper.readValue(result("direct:getUser"), new TypeReference<>() {
        });

        assertEquals(Map.of("domain_id", "d1", "name", "User 15", "pwd_status", true, "is_domain_owner", false,
                "last_login_time", "2026-01-01T10:00:00Z", "xuser_id", "x1"), user);
    }

    @Test
    void listUsersGivesApiFieldNames() throws Exception {
        List<Map<String, Object>> users = mapper.readValue(result("direct:listUsers"), new TypeReference<>() {
        });

        assertEquals(List.of(Map.of("domain_id", "d1", "name", "User 1", "last_project_id", "p1",
                "password_expires_at", "2027-01-01T10:00:00Z", "pwd_strength", "high")), users);
    }

    @Test
    void listGroupsGivesApiFieldNames() throws Exception {
        List<Map<String, Object>> groups = mapper.readValue(result("direct:listGroups"), new TypeReference<>() {
        });

        assertEquals(List.of(Map.of("domain_id", "d1", "name", "Group 1", "create_time", 1767261600000L)), groups);
    }

    @Test
    void updateGroupGivesApiFieldNames() {
        Exchange exchange = send("direct:updateGroup", GROUP_JSON);

        assertNull(exchange.getException(), () -> "updateGroup failed: " + exchange.getException());
        assertEquals("{\"description\":\"Group description\",\"domain_id\":\"123\",\"name\":\"Group 43\"}",
                exchange.getMessage().getBody(String.class));
    }

    private static void assertUserOption(UpdateUserOption option) {
        assertEquals("User 19", option.getName());
        assertEquals("Zürich ✓", option.getDescription());
        assertEquals(Boolean.TRUE, option.getPwdStatus());
    }

    private static void assertGroupOption(KeystoneUpdateGroupOption option) {
        assertEquals("Group 43", option.getName());
        assertEquals("Zürich ✓", option.getDescription());
        assertEquals("123", option.getDomainId());
    }

    private Exchange send(String uri, Object body) {
        return template.send(uri, e -> e.getMessage().setBody(body));
    }

    private String result(String uri) {
        Exchange exchange = send(uri, null);
        assertNull(exchange.getException(), () -> uri + " failed: " + exchange.getException());
        return exchange.getMessage().getBody(String.class);
    }

    private UpdateUserOption updateUser(Object body) {
        Exchange exchange = send("direct:updateUser", body);

        assertNull(exchange.getException(), () -> "updateUser failed: " + exchange.getException());
        assertNotNull(client.userRequest);
        assertEquals(testConfiguration.getProperty("userId"), client.userRequest.getUserId());
        return client.userRequest.getBody().getUser();
    }

    private KeystoneUpdateGroupOption updateGroup(Object body) {
        Exchange exchange = send("direct:updateGroup", body);

        assertNull(exchange.getException(), () -> "updateGroup failed: " + exchange.getException());
        assertNotNull(client.groupRequest);
        assertEquals(testConfiguration.getProperty("groupId"), client.groupRequest.getGroupId());
        return client.groupRequest.getBody().getGroup();
    }

    /**
     * Keeps the update requests, and gives results with fields whose API name is not the Java name.
     */
    static class CapturingClient extends IAMMockClient {
        UpdateUserRequest userRequest;
        KeystoneUpdateGroupRequest groupRequest;

        CapturingClient() {
            super(null);
        }

        @Override
        public UpdateUserResponse updateUser(UpdateUserRequest request) {
            this.userRequest = request;
            return super.updateUser(request);
        }

        @Override
        public KeystoneUpdateGroupResponse keystoneUpdateGroup(KeystoneUpdateGroupRequest request) {
            this.groupRequest = request;
            return super.keystoneUpdateGroup(request);
        }

        @Override
        public ShowUserResponse showUser(ShowUserRequest request) {
            ShowUserResult user = new ShowUserResult()
                    .withDomainId("d1")
                    .withName("User 15")
                    .withPwdStatus(true)
                    .withIsDomainOwner(false)
                    .withLastLoginTime("2026-01-01T10:00:00Z")
                    .withXuserId("x1");
            return new ShowUserResponse().withUser(user);
        }

        @Override
        public KeystoneListUsersResponse keystoneListUsers(KeystoneListUsersRequest request) {
            KeystoneListUsersResult user = new KeystoneListUsersResult()
                    .withDomainId("d1")
                    .withName("User 1")
                    .withLastProjectId("p1")
                    .withPasswordExpiresAt("2027-01-01T10:00:00Z")
                    .withPwdStrength("high");
            return new KeystoneListUsersResponse().withUsers(List.of(user));
        }

        @Override
        public KeystoneListGroupsResponse keystoneListGroups(KeystoneListGroupsRequest request) {
            KeystoneGroupResult group = new KeystoneGroupResult()
                    .withDomainId("d1")
                    .withName("Group 1")
                    .withCreateTime(1767261600000L);
            return new KeystoneListGroupsResponse().withGroups(List.of(group));
        }
    }
}
