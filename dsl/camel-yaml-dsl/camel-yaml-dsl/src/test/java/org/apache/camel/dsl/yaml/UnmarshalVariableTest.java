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
package org.apache.camel.dsl.yaml;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

class UnmarshalVariableTest extends YamlTestSupport {

    private static final String ROUTES = """
            - route:
                from:
                  uri: direct:send
                  steps:
                    - setVariable:
                        name: hello
                        simple:
                          expression: Camel
                    - to:
                        uri: mock:before
                    - unmarshal:
                        custom: myDF
                        variableSend: hello
                    - to:
                        uri: mock:result
            - route:
                from:
                  uri: direct:receive
                  steps:
                    - unmarshal:
                        custom: myDF
                        variableReceive: bye
                    - to:
                        uri: mock:after
                    - setBody:
                        simple:
                          expression: "${variable:bye}"
                    - to:
                        uri: mock:result
            - route:
                from:
                  uri: direct:sendAndReceive
                  steps:
                    - setVariable:
                        name: hello
                        simple:
                          expression: Camel
                    - to:
                        uri: mock:before
                    - unmarshal:
                        custom: myDF
                        variableReceive: bye
                        variableSend: hello
                    - to:
                        uri: mock:result
            - route:
                from:
                  uri: direct:foo
                  steps:
                    - transform:
                        simple:
                          expression: "Bye ${body}"
            """;

    @Test
    void unmarshalVariableSend() throws Exception {
        loadRoutes(ROUTES);

        withMock("mock:before", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("hello", "Camel");
        });
        withMock("mock:result", mock -> {
            mock.expectedBodiesReceived("Bye Camel");
            mock.expectedVariableReceived("hello", "Camel");
        });

        context.getRegistry().bind("myDF", new MyByeDataFormat());
        context.start();

        withTemplate(t -> t.to("direct:send").withBody("World").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void unmarshalVariableReceive() throws Exception {
        loadRoutes(ROUTES);

        withMock("mock:after", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("bye", "Bye World");
        });
        withMock("mock:result", mock -> {
            mock.expectedBodiesReceived("Bye World");
            mock.expectedVariableReceived("bye", "Bye World");
        });

        context.getRegistry().bind("myDF", new MyByeDataFormat());
        context.start();

        withTemplate(t -> t.to("direct:receive").withBody("World").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void unmarshalVariableSendAndReceive() throws Exception {
        loadRoutes(ROUTES);

        withMock("mock:before", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("hello", "Camel");
        });
        withMock("mock:result", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("bye", "Bye Camel");
        });

        context.getRegistry().bind("myDF", new MyByeDataFormat());
        context.start();

        withTemplate(t -> t.to("direct:sendAndReceive").withBody("World").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    public static class MyByeDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            // noop
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            return "Bye " + exchange.getContext().getTypeConverter().convertTo(String.class, exchange, stream);
        }
    }
}
