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

import org.apache.camel.component.openstack.AbstractProducerTestSupport;
import org.junit.Before;
import org.mockito.Mock;
import org.openstack4j.api.storage.ObjectStorageContainerService;
import org.openstack4j.api.storage.ObjectStorageObjectService;
import org.openstack4j.api.storage.ObjectStorageService;

import static org.mockito.Mockito.when;

public class SwiftProducerTestSupport extends AbstractProducerTestSupport {

    @Mock
    protected SwiftEndpoint endpoint;

    @Mock
    protected ObjectStorageService objectStorageService;

    @Mock
    protected ObjectStorageContainerService containerService;

    @Mock
    protected ObjectStorageObjectService objectService;

    @Before
    public void setUpComputeService() {
        when(client.objectStorage()).thenReturn(objectStorageService);
        when(objectStorageService.containers()).thenReturn(containerService);
        when(objectStorageService.objects()).thenReturn(objectService);
    }


}
