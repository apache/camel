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

import java.net.Socket;
import java.util.Map;

import com.splunk.Service;

import org.apache.camel.CamelContext;

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

    MockConnectionSettings(Service service, Socket socket) {
        setConnectionFactory(new MockConnectionFactory(service));
    }

    class MockConnectionFactory extends SplunkConnectionFactory {
        private Service service;

        MockConnectionFactory(Service service) {
            super("foo", "bar");
            this.service = service;
        }

        @Override
        public Service createService(CamelContext camelContext) {
            return service;
        }
    }
}
