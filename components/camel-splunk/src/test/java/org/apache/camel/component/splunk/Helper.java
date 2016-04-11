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
package org.apache.camel.component.splunk;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

import com.splunk.Args;
import com.splunk.Index;
import com.splunk.IndexCollection;
import com.splunk.InputCollection;
import com.splunk.Service;
import com.splunk.TcpInput;

import org.apache.camel.CamelContext;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class Helper {

    private Helper() {
    }

    public static DefaultSplunkConfigurationFactory mockComponent(final Service service, final Socket socket) {
        return new DefaultSplunkConfigurationFactory() {
            @Override
            public SplunkConfiguration parseMap(Map<String, Object> parameters) {
                return new MockConnectionSettings(service, socket);
            }
        };
    }

}

final class MockConnectionSettings extends SplunkConfiguration {
    private Service service;
    private Socket socket;

    public MockConnectionSettings(Service service, Socket socket) {
        this.service = service;
        this.socket = socket;
        mockSplunkWriterApi();
        setConnectionFactory(new MockConnectionFactory(service));
    }

    private void mockSplunkWriterApi() {
        try {
            Index index = mock(Index.class);
            IndexCollection indexColl = mock(IndexCollection.class);
            when(service.getIndexes()).thenReturn(indexColl);
            InputCollection inputCollection = mock(InputCollection.class);
            when(service.getInputs()).thenReturn(inputCollection);
            TcpInput input = mock(TcpInput.class);
            when(input.attach()).thenReturn(socket);
            when(inputCollection.get(anyString())).thenReturn(input);
            when(indexColl.get(anyString())).thenReturn(index);
            when(index.attach(isA(Args.class))).thenReturn(socket);
            when(socket.getOutputStream()).thenReturn(System.out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class MockConnectionFactory extends SplunkConnectionFactory {
        private Service service;

        public MockConnectionFactory(Service service) {
            super("foo", "bar");
            this.service = service;
        }

        @Override
        public Service createService(CamelContext camelContext) {
            return service;
        }
    }
}
