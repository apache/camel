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
package org.apache.camel.component.atlasmap;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import io.atlasmap.api.AtlasContext;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.v2.AtlasMapping;
import io.atlasmap.v2.Audits;
import io.atlasmap.v2.DataSource;
import io.atlasmap.v2.DataSourceType;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class AtlasMapEndpointTest {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasMapEndpointTest.class);

    @Test
    public void testNoDataSource() throws Exception {
        perform(new ArrayList<>(), null, null, false);
    }

    @Test
    public void testDocId() throws Exception {
        List<DataSource> ds = new ArrayList<>();
        DataSource source = new DataSource();
        source.setDataSourceType(DataSourceType.SOURCE);
        source.setId("my-source-doc");
        ds.add(source);
        DataSource target = new DataSource();
        target.setDataSourceType(DataSourceType.TARGET);
        target.setId("my-target-doc");
        ds.add(target);
        perform(ds, "my-source-doc", "my-target-doc", false);
    }

    private void execute() throws Exception {
        perform(new ArrayList<>(), null, null, true);
    }

    @Test
    public void noConversionIfNoDataSource() throws Exception {
        assertThrows(AssertionFailedError.class, this::execute);
    }

    @Test
    public void noConversionIfJavaDataSource() throws Exception {
        final List<DataSource> dataSources = new ArrayList<>();
        final DataSource dataSource = new DataSource();
        dataSource.setDataSourceType(DataSourceType.SOURCE);
        dataSource.setUri("atlas:java:some.Type");
        dataSources.add(dataSource);
        assertThrows(AssertionFailedError.class, () -> {
            perform(dataSources, null, null, true);
        });
    }

    @Test
    public void doConversionIfJsonDataSource() throws Exception {
        final List<DataSource> dataSources = new ArrayList<>();
        final DataSource dataSource = new DataSource();
        dataSource.setDataSourceType(DataSourceType.SOURCE);
        dataSource.setUri("atlas:json:SomeType");
        dataSources.add(dataSource);
        perform(dataSources, null, null, true);
    }

    @Test
    public void noConversionIfJsonTargetDataSource() throws Exception {
        final List<DataSource> dataSources = new ArrayList<>();
        final DataSource dataSource = new DataSource();
        dataSource.setDataSourceType(DataSourceType.TARGET);
        dataSource.setUri("atlas:json:SomeType");
        dataSources.add(dataSource);
        assertThrows(AssertionFailedError.class, () -> {
            perform(dataSources, null, null, true);
        });
    }

    @Test
    public void doConversionIfXmlDataSource() throws Exception {
        final List<DataSource> dataSources = new ArrayList<>();
        final DataSource dataSource = new DataSource();
        dataSource.setDataSourceType(DataSourceType.SOURCE);
        dataSource.setUri("atlas:xml:SomeType");
        dataSources.add(dataSource);
        perform(dataSources, null, null, true);
    }

    @Test
    public void noConversionIfXmlTargetDataSource() throws Exception {
        final List<DataSource> dataSources = new ArrayList<>();
        final DataSource dataSource = new DataSource();
        dataSource.setDataSourceType(DataSourceType.TARGET);
        dataSource.setUri("atlas:xml:SomeType");
        dataSources.add(dataSource);
        assertThrows(AssertionFailedError.class, () -> {
            perform(dataSources, null, null, true);
        });
    }

    private void perform(List<DataSource> dataSources, String sourceDocId, String targetDocId, boolean fromStream)
            throws Exception {
        final AtlasMapping mapping = new AtlasMapping();
        mapping.getDataSource().addAll(dataSources);
        final AtlasContext context = spy(AtlasContext.class);
        final AtlasSession session = spy(AtlasSession.class);
        when(context.createSession()).thenReturn(session);
        when(session.getAtlasContext()).thenReturn(context);
        when(session.getMapping()).thenReturn(mapping);
        when(session.getAudits()).thenReturn(new Audits());
        final AtlasMapEndpoint endpoint = new AtlasMapEndpoint("atlasmap:test.xml", new AtlasMapComponent(), "test.xml");
        endpoint.setAtlasContext(context);
        final Exchange exchange = spy(Exchange.class);
        final Message inMessage = spy(Message.class);
        when(inMessage.getBody()).thenReturn(fromStream ? new ByteArrayInputStream("{test}".getBytes()) : "{test}");
        when(inMessage.getBody(String.class)).thenReturn("{test}");
        when(exchange.getIn()).thenReturn(inMessage);
        if (sourceDocId == null) {
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) {
                    LOG.debug("setDefaultSourceDocument({})", invocation.getArgument(0).toString());
                    assertEquals("{test}", invocation.getArgument(0).toString());
                    return null;
                }
            }).when(session).setDefaultSourceDocument(any());
        } else {
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) {
                    LOG.debug("setSourceDocument({}, {})", invocation.getArgument(0), invocation.getArgument(1));
                    assertEquals(sourceDocId, invocation.getArgument(0));
                    assertEquals("{test}", invocation.getArgument(1));
                    return null;
                }
            }).when(session).setSourceDocument(any(), any());
        }
        final Message outMessage = spy(Message.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                LOG.debug("setBody({})", invocation.getArgument(0).toString());
                assertEquals("<target/>", invocation.getArgument(0));
                return null;
            }
        }).when(outMessage).setBody(any());
        doNothing().when(outMessage).setHeaders(any());
        if (targetDocId == null) {
            when(session.getDefaultTargetDocument()).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    LOG.debug("getDefaultTargetDocument()");
                    return "<target/>";
                }
            });
        } else {
            when(session.getTargetDocument(any())).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    LOG.debug("getTargetDocument({})", invocation.getArgument(0).toString());
                    assertEquals(targetDocId, invocation.getArgument(0));
                    return "<target/>";
                }
            });
        }
        when(exchange.getMessage()).thenReturn(outMessage);
        endpoint.onExchange(exchange);
    }
}
