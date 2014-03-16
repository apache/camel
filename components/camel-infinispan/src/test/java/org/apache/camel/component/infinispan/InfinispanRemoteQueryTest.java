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
package org.apache.camel.component.infinispan;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.processor.query.HavingQueryBuilderStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.commons.util.Util;
import org.infinispan.protostream.SerializationContext;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.hamcrest.core.Is.is;

@Ignore(value = "Run with standalone Infinispan server that has indexing enabled. "
        + "Also you need jboss-client.jar on the classpath to register book.protobin over JMX")
public class InfinispanRemoteQueryTest extends CamelTestSupport {
    public static final String BOOK_PROTOBIN = "/book.protobin";
    public static final String SERVER_URL = "127.0.0.1";
    protected HavingQueryBuilderStrategy queryBuilderStrategy;
    protected RemoteCacheManager cacheContainer;

    @Override
    @Before
    public void setUp() throws Exception {
        Configuration config = new ConfigurationBuilder()
                .addServers(SERVER_URL)
                .marshaller(new ProtoStreamMarshaller())
                .build();

        cacheContainer = new RemoteCacheManager(config);
        queryBuilderStrategy = new HavingQueryBuilderStrategy(Book.class, "title", "Camel");

        SerializationContext srcCtx = ProtoStreamMarshaller.getSerializationContext(cacheContainer);
        srcCtx.registerProtofile(BOOK_PROTOBIN);
        srcCtx.registerMarshaller(Book.class, new BookMarshaller());

        updateServerSchema();

        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("cacheContainer", cacheContainer);
        registry.bind("queryBuilderStrategy", queryBuilderStrategy);
        return registry;
    }

    @Test
    public void findsCacheEntryBasedOnTheValue() throws Exception {
        final Book camelBook = new Book("1", "Camel", "123");
        final Book activeMQBook = new Book("2", "ActiveMQ", "124");

        Exchange request = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, camelBook.getId());
                exchange.getIn().setHeader(InfinispanConstants.VALUE, camelBook);
            }
        });

        assertNull(request.getException());

        request = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, activeMQBook.getId());
                exchange.getIn().setHeader(InfinispanConstants.VALUE, activeMQBook);
            }
        });

        assertNull(request.getException());

        Exchange exchange = template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.QUERY);
            }
        });

        List<Book> result = exchange.getIn().getHeader(InfinispanConstants.RESULT, List.class);
        assertNull(exchange.getException());
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(camelBook));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&queryBuilderStrategy=#queryBuilderStrategy");
            }
        };
    }

    private void updateServerSchema() throws Exception {
        JMXServiceURL serviceURL = new JMXServiceURL("service:jmx:remoting-jmx://" + SERVER_URL + ":" + "9999");
        JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
        MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();

        byte[] descriptor = readClasspathResource(BOOK_PROTOBIN);
        ObjectName objName = new ObjectName("jboss.infinispan:type=RemoteQuery,name=\"local\",component=ProtobufMetadataManager");
        mBeanServerConnection.invoke(objName, "registerProtofile", new Object[]{descriptor}, new String[]{byte[].class.getName()});
    }

    private byte[] readClasspathResource(String classPathResource) throws IOException {
        InputStream is = getClass().getResourceAsStream(classPathResource);
        try {
            return Util.readStream(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }
}
