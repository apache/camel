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
package org.apache.camel.component.openstack.swift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.producer.ContainerProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.storage.ObjectStorageContainerService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.CreateUpdateContainerOptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ContainerProducerTest extends SwiftProducerTestSupport {

    private static final String CONTAINER_NAME = "containerName";

    @Mock
    private SwiftContainer mockOsContainer;

    @Mock
    private ObjectStorageContainerService containerService;

    @Captor
    private ArgumentCaptor<String> nameCaptor;

    @Captor
    private ArgumentCaptor<Map<String, String>> dataCaptor;

    @Captor
    private ArgumentCaptor<String> containerNameCaptor;

    @Captor
    private ArgumentCaptor<CreateUpdateContainerOptions> optionsCaptor;

    @Captor
    private ArgumentCaptor<ContainerListOptions> containerListOptionsCaptor;

    @Before
    public void setUp() {
        when(objectStorageService.containers()).thenReturn(containerService);

        producer = new ContainerProducer(endpoint, client);
    }

    @Test
    public void createTestWithoutOptions() throws Exception {
        when(containerService.create(anyString(), isNull())).thenReturn(ActionResponse.actionSuccess());
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);

        producer.process(exchange);

        verify(containerService).create(containerNameCaptor.capture(), optionsCaptor.capture());
        assertEquals(CONTAINER_NAME, containerNameCaptor.getValue());
        assertNull(optionsCaptor.getValue());

        assertFalse(msg.isFault());
    }

    @Test
    public void createTestWithOptions() throws Exception {
        when(containerService.create(anyString(), any())).thenReturn(ActionResponse.actionSuccess());
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        final CreateUpdateContainerOptions options = CreateUpdateContainerOptions.create().accessAnybodyRead();
        msg.setBody(options);
        producer.process(exchange);

        verify(containerService).create(containerNameCaptor.capture(), optionsCaptor.capture());
        assertEquals(CONTAINER_NAME, containerNameCaptor.getValue());
        assertEquals(options, optionsCaptor.getValue());
        assertFalse(msg.isFault());
    }

    @Test
    public void getTest() throws Exception {
        List<SwiftContainer> list = new ArrayList<>();
        list.add(mockOsContainer);
        doReturn(list).when(containerService).list(any());
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET);

        msg.setHeader(SwiftConstants.LIMIT, 10);
        msg.setHeader(SwiftConstants.DELIMITER, 'x');

        producer.process(exchange);
        verify(containerService).list(containerListOptionsCaptor.capture());
        Map<String, String> options = containerListOptionsCaptor.getValue().getOptions();
        assertEquals(String.valueOf(10), options.get(SwiftConstants.LIMIT));
        assertEquals("x", options.get(SwiftConstants.DELIMITER));
        assertEquals(list, msg.getBody(List.class));
    }

    @Test
    public void getAllFromContainerTest() throws Exception {
        List<SwiftContainer> list = new ArrayList<>();
        list.add(mockOsContainer);
        doReturn(list).when(containerService).list();

        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET_ALL);

        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);

        producer.process(exchange);
        assertEquals(mockOsContainer, msg.getBody(List.class).get(0));
    }

    @Test
    public void deleteObjectTest() throws Exception {
        when(containerService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);

        producer.process(exchange);

        verify(containerService).delete(containerNameCaptor.capture());
        assertEquals(CONTAINER_NAME, containerNameCaptor.getValue());

        assertFalse(msg.isFault());
    }

    @Test
    public void deleteObjectFailTest() throws Exception {
        final String failMessage = "fail";
        when(containerService.delete(anyString())).thenReturn(ActionResponse.actionFailed(failMessage, 401));
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);

        producer.process(exchange);

        assertTrue(msg.isFault());
        assertTrue(msg.getBody(String.class).contains(failMessage));
    }

    @Test
    public void createUpdateMetadataTest() throws Exception {
        final Map<String, String> md = new HashMap<>();
        md.put("key", "val");

        msg.setHeader(OpenstackConstants.OPERATION, SwiftConstants.CREATE_UPDATE_METADATA);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        msg.setBody(md);

        producer.process(exchange);

        verify(containerService).updateMetadata(nameCaptor.capture(), dataCaptor.capture());

        assertEquals(CONTAINER_NAME, nameCaptor.getValue());
        assertEquals(md, dataCaptor.getValue());
    }

    @Test
    public void getMetadataTest() throws Exception {
        final Map<String, String> md = new HashMap<>();
        md.put("key", "val");

        when(containerService.getMetadata(CONTAINER_NAME)).thenReturn(md);
        msg.setHeader(OpenstackConstants.OPERATION, SwiftConstants.GET_METADATA);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);

        producer.process(exchange);

        assertEquals(md, msg.getBody(Map.class));
    }
}
