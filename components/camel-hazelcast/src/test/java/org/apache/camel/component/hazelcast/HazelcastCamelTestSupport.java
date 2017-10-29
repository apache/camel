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
package org.apache.camel.component.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

public class HazelcastCamelTestSupport extends CamelTestSupport {

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MockitoAnnotations.initMocks(this);
        CamelContext context = super.createCamelContext();
        HazelcastCamelTestHelper.registerHazelcastComponents(context, hazelcastInstance);
        trainHazelcastInstance(hazelcastInstance);
        return context;
    }

    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {

    }

    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {

    }

    @After
    public final void verifyHazelcastInstanceMock() {
        verifyHazelcastInstance(hazelcastInstance);
        verifyNoMoreInteractions(hazelcastInstance);
    }


}
