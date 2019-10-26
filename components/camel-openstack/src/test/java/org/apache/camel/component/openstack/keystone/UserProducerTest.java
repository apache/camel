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
package org.apache.camel.component.openstack.keystone;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.keystone.producer.UserProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.identity.v3.UserService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.identity.v3.User;
import org.openstack4j.model.network.Network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserProducerTest extends KeystoneProducerTestSupport {

    private User dummyUser;

    @Mock
    private User testOSuser;

    @Mock
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<String> userIdCaptor;

    @Before
    public void setUp() {
        when(identityService.users()).thenReturn(userService);

        producer = new UserProducer(endpoint, client);

        when(userService.create(any())).thenReturn(testOSuser);
        when(userService.get(anyString())).thenReturn(testOSuser);

        List<User> getAllList = new ArrayList<>();
        getAllList.add(testOSuser);
        getAllList.add(testOSuser);
        doReturn(getAllList).when(userService).list();

        dummyUser = createUser();

        when(testOSuser.getName()).thenReturn(dummyUser.getName());
        when(testOSuser.getDescription()).thenReturn(dummyUser.getDescription());
        when(testOSuser.getPassword()).thenReturn(dummyUser.getPassword());
        when(testOSuser.getDomainId()).thenReturn(dummyUser.getDomainId());
        when(testOSuser.getEmail()).thenReturn(dummyUser.getEmail());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyUser.getName());
        msg.setHeader(KeystoneConstants.DESCRIPTION, dummyUser.getDescription());
        msg.setHeader(KeystoneConstants.DOMAIN_ID, dummyUser.getDomainId());
        msg.setHeader(KeystoneConstants.PASSWORD, dummyUser.getPassword());
        msg.setHeader(KeystoneConstants.EMAIL, dummyUser.getEmail());

        producer.process(exchange);

        verify(userService).create(userCaptor.capture());

        assertEqualsUser(dummyUser, userCaptor.getValue());
    }

    @Test
    public void getTest() throws Exception {
        final String id = "id";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, id);

        producer.process(exchange);

        verify(userService).get(userIdCaptor.capture());

        assertEquals(id, userIdCaptor.getValue());
        assertEqualsUser(testOSuser, msg.getBody(User.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Network> result = msg.getBody(List.class);
        assertTrue(result.size() == 2);
        assertEquals(testOSuser, result.get(0));
    }

    @Test
    public void updateTest() throws Exception {
        final String id = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);

        when(testOSuser.getId()).thenReturn(id);
        final String newDescription = "ndesc";
        when(testOSuser.getDescription()).thenReturn(newDescription);

        when(userService.update(any())).thenReturn(testOSuser);
        msg.setBody(testOSuser);

        producer.process(exchange);

        verify(userService).update(userCaptor.capture());

        assertEqualsUser(testOSuser, userCaptor.getValue());
        assertNotNull(userCaptor.getValue().getId());
        assertEquals(newDescription, msg.getBody(User.class).getDescription());
    }

    @Test
    public void deleteTest() throws Exception {
        when(userService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String networkID = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, networkID);

        producer.process(exchange);

        verify(userService).delete(userIdCaptor.capture());
        assertEquals(networkID, userIdCaptor.getValue());
    }

    private void assertEqualsUser(User old, User newUser) {
        assertEquals(old.getName(), newUser.getName());
        assertEquals(old.getDomainId(), newUser.getDomainId());
        assertEquals(old.getPassword(), newUser.getPassword());
        assertEquals(old.getDescription(), newUser.getDescription());
        assertEquals(old.getEmail(), newUser.getEmail());
    }

    private User createUser() {
        return Builders.user()
                .name("User name")
                .domainId("domainId")
                .password("password")
                .description("desc")
                .email("email@mail.com")
                .build();
    }

}
