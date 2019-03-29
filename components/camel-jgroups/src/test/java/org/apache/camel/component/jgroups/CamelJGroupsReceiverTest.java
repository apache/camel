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
package org.apache.camel.component.jgroups;

import org.apache.camel.Processor;
import org.jgroups.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.BDDMockito.willThrow;

@RunWith(MockitoJUnitRunner.class)
public class CamelJGroupsReceiverTest {

    // Fixtures

    @InjectMocks
    CamelJGroupsReceiver receiver;

    @Mock
    JGroupsEndpoint jGroupsEndpoint;

    @Mock
    Processor processor;

    // Tests

    @Test(expected = JGroupsException.class)
    public void shouldHandleProcessingException() throws Exception {
        // Given
        willThrow(Exception.class).given(processor).process(ArgumentMatchers.isNull());
        Message message = new Message(null, "someMessage");
        message.setSrc(null);
        // When
        receiver.receive(message);
    }

}
