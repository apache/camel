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
package org.apache.camel.component.openstack.keystone;

import org.apache.camel.component.openstack.AbstractProducerTestSupport;
import org.junit.Before;
import org.mockito.Mock;
import org.openstack4j.api.identity.v3.DomainService;
import org.openstack4j.api.identity.v3.GroupService;
import org.openstack4j.api.identity.v3.IdentityService;
import org.openstack4j.api.identity.v3.ProjectService;
import org.openstack4j.api.identity.v3.RegionService;
import org.openstack4j.api.identity.v3.UserService;

import static org.mockito.Mockito.when;

public class KeystoneProducerTestSupport extends AbstractProducerTestSupport {

    @Mock
    protected KeystoneEndpoint endpoint;

    @Mock
    protected IdentityService identityService;

    @Mock
    protected DomainService domainService;

    @Mock
    protected GroupService groupService;

    @Mock
    protected ProjectService projectService;

    @Mock
    protected RegionService regionService;

    @Mock
    protected UserService userService;

    @Before
    public void setUpComputeService() {
        when(client.identity()).thenReturn(identityService);
        when(identityService.domains()).thenReturn(domainService);
        when(identityService.groups()).thenReturn(groupService);
        when(identityService.projects()).thenReturn(projectService);
        when(identityService.regions()).thenReturn(regionService);
        when(identityService.users()).thenReturn(userService);
    }
}
