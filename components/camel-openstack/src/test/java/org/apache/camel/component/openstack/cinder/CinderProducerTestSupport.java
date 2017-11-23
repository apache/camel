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
package org.apache.camel.component.openstack.cinder;

import org.apache.camel.component.openstack.AbstractProducerTestSupport;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.storage.BlockStorageService;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CinderProducerTestSupport extends AbstractProducerTestSupport {

    @Mock
    protected CinderEndpoint endpoint;

    @Mock
    protected BlockStorageService blockStorageService;

    @Before
    public void setUpComputeService() {
        when(client.blockStorage()).thenReturn(blockStorageService);
    }
}
