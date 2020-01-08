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
package org.apache.camel.component.beanstalk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.surftools.BeanstalkClient.Client;
import org.apache.camel.CamelContext;

public final class Helper {

    private Helper() {
    }

    public static ConnectionSettings mockConn(final Client client) {
        return new MockConnectionSettings(client);
    }

    public static void mockComponent(final Client client) {
        BeanstalkComponent.setConnectionSettingsFactory(new ConnectionSettingsFactory() {
            @Override
            public ConnectionSettings parseUri(String uri) {
                return new MockConnectionSettings(client);
            }
        });
    }

    public static void revertComponent() {
        BeanstalkComponent.setConnectionSettingsFactory(ConnectionSettingsFactory.DEFAULT);
    }

    public static BeanstalkEndpoint getEndpoint(String uri, CamelContext context, Client client) throws Exception {
        BeanstalkEndpoint endpoint = new BeanstalkEndpoint(uri, context.getComponent("beanstalk"), mockConn(client), "");
        context.addEndpoint(uri, endpoint);
        return endpoint;
    }

    public static byte[] stringToBytes(final String s) throws IOException {
        final ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        final DataOutputStream dataStream = new DataOutputStream(byteOS);

        try {
            dataStream.writeBytes(s);
            dataStream.flush();
            return byteOS.toByteArray();
        } finally {
            dataStream.close();
            byteOS.close();
        }
    }
}

class MockConnectionSettings extends ConnectionSettings {
    private final Client client;

    MockConnectionSettings(Client client) {
        super("tube");
        this.client = client;
    }

    @Override
    public Client newReadingClient(boolean useBlockIO) {
        return client;
    }

    @Override
    public Client newWritingClient() {
        return client;
    }
}
