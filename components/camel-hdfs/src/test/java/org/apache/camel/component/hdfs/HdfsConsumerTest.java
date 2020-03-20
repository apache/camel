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
package org.apache.camel.component.hdfs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.apache.camel.component.hdfs.HdfsConstants.DEFAULT_OPENED_SUFFIX;
import static org.apache.camel.component.hdfs.HdfsConstants.DEFAULT_READ_SUFFIX;
import static org.apache.camel.component.hdfs.HdfsTestSupport.CWD;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HdfsConsumerTest {

    private HdfsEndpoint endpoint;
    private Processor processor;
    private HdfsConfiguration endpointConfig;
    private HdfsInfoFactory hdfsInfoFactory;
    private CamelContext context;
    private FileSystem fileSystem;
    private Configuration configuration;

    private HdfsConsumer underTest;

    @Before
    public void setUp() throws Exception {
        endpoint = mock(HdfsEndpoint.class);
        processor = mock(Processor.class);
        endpointConfig = mock(HdfsConfiguration.class);
        hdfsInfoFactory = mock(HdfsInfoFactory.class);

        HdfsInfo hdfsInfo = mock(HdfsInfo.class);
        fileSystem = mock(FileSystem.class);
        configuration = mock(Configuration.class);
        Path path = mock(Path.class);

        when(hdfsInfoFactory.newHdfsInfo(anyString())).thenReturn(hdfsInfo);
        when(hdfsInfoFactory.getEndpointConfig()).thenReturn(endpointConfig);
        when(hdfsInfoFactory.newHdfsInfo(anyString())).thenReturn(hdfsInfo);

        when(hdfsInfo.getFileSystem()).thenReturn(fileSystem);
        when(hdfsInfo.getConfiguration()).thenReturn(configuration);
        when(hdfsInfo.getPath()).thenReturn(path);

        when(endpointConfig.getReadSuffix()).thenReturn(DEFAULT_READ_SUFFIX);
        when(endpointConfig.getOpenedSuffix()).thenReturn(DEFAULT_OPENED_SUFFIX);

        context = new DefaultCamelContext();
    }

    @Test
    public void doStartWithoutHdfsSetup() throws Exception {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        when(endpointConfig.getFileSystemType()).thenReturn(HdfsFileSystemType.LOCAL);
        when(endpointConfig.getPath()).thenReturn(hdfsPath);
        when(endpointConfig.isConnectOnStartup()).thenReturn(false);
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.getEndpointUri()).thenReturn(hdfsPath);

        underTest = new HdfsConsumer(endpoint, processor, endpointConfig, hdfsInfoFactory, new StringBuilder(hdfsPath));

        // when
        underTest.doStart();

        // then
        verify(hdfsInfoFactory, times(0)).newHdfsInfo(anyString());
    }

    @Test
    public void doStartWithHdfsSetup() throws Exception {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        when(endpointConfig.getFileSystemType()).thenReturn(HdfsFileSystemType.LOCAL);
        when(endpointConfig.getPath()).thenReturn(hdfsPath);
        when(endpointConfig.isConnectOnStartup()).thenReturn(true);
        when(endpointConfig.getFileSystemLabel(anyString())).thenReturn("TEST_FS_LABEL");
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.getEndpointUri()).thenReturn(hdfsPath);

        underTest = new HdfsConsumer(endpoint, processor, endpointConfig, hdfsInfoFactory, new StringBuilder(hdfsPath));

        // when
        underTest.doStart();

        // then
        verify(hdfsInfoFactory, times(1)).newHdfsInfo(hdfsPath);
    }

    @Test
    public void doPollFromExistingLocalFile() throws Exception {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        when(endpointConfig.getFileSystemType()).thenReturn(HdfsFileSystemType.LOCAL);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.getPath()).thenReturn(hdfsPath);
        when(endpointConfig.getOwner()).thenReturn("spiderman");
        when(endpointConfig.isConnectOnStartup()).thenReturn(true);
        when(endpointConfig.getFileSystemLabel(anyString())).thenReturn("TEST_FS_LABEL");
        when(endpointConfig.getChunkSize()).thenReturn(100 * 1000);
        when(endpointConfig.getMaxMessagesPerPoll()).thenReturn(10);
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.createExchange()).thenReturn(new DefaultExchange(context));
        when(endpoint.getEndpointUri()).thenReturn(hdfsPath);

        when(fileSystem.isFile(any(Path.class))).thenReturn(true);

        FileStatus[] fileStatuses = new FileStatus[1];
        FileStatus fileStatus = mock(FileStatus.class);
        fileStatuses[0] = fileStatus;
        when(fileSystem.globStatus(any(Path.class))).thenReturn(fileStatuses);
        when(fileStatus.getPath()).thenReturn(new Path(hdfsPath));
        when(fileStatus.isFile()).thenReturn(true);
        when(fileStatus.isDirectory()).thenReturn(false);
        when(fileStatus.getOwner()).thenReturn("spiderman");

        String normalFile = CWD.getAbsolutePath() + "/src/test/resources/hdfs/normal_file.txt";
        FSDataInputStream fsDataInputStream = new FSDataInputStream(new MockDataInputStream(normalFile));
        when(fileSystem.rename(any(Path.class), any(Path.class))).thenReturn(true);
        when(fileSystem.open(any(Path.class))).thenReturn(fsDataInputStream);

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        underTest = new HdfsConsumer(endpoint, processor, endpointConfig, hdfsInfoFactory, new StringBuilder(hdfsPath));

        // when
        int actual = underTest.doPoll();

        // then
        assertThat(actual, is(1));
        verify(processor, times(1)).process(exchangeCaptor.capture());
        Exchange exchange = exchangeCaptor.getValue();
        assertThat(exchange, notNullValue());

        ByteArrayOutputStream body = exchange.getIn().getBody(ByteArrayOutputStream.class);
        assertThat(body, notNullValue());
        assertThat(body.toString(), startsWith("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam eget fermentum arcu, vel dignissim ipsum."));
    }

    @Test
    public void doPollFromExistingLocalFileWithStreamDownload() throws Exception {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        when(endpointConfig.getFileSystemType()).thenReturn(HdfsFileSystemType.LOCAL);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.getPath()).thenReturn(hdfsPath);
        when(endpointConfig.getOwner()).thenReturn("spiderman");
        when(endpointConfig.isConnectOnStartup()).thenReturn(true);
        when(endpointConfig.getFileSystemLabel(anyString())).thenReturn("TEST_FS_LABEL");
        when(endpointConfig.getChunkSize()).thenReturn(100 * 1000);
        when(endpointConfig.isStreamDownload()).thenReturn(true);
        when(endpointConfig.getMaxMessagesPerPoll()).thenReturn(10);
        when(endpoint.getCamelContext()).thenReturn(context);
        when(endpoint.createExchange()).thenReturn(new DefaultExchange(context));
        when(endpoint.getEndpointUri()).thenReturn(hdfsPath);

        when(fileSystem.isFile(any(Path.class))).thenReturn(true);

        FileStatus[] fileStatuses = new FileStatus[1];
        FileStatus fileStatus = mock(FileStatus.class);
        fileStatuses[0] = fileStatus;
        when(fileSystem.globStatus(any(Path.class))).thenReturn(fileStatuses);
        when(fileStatus.getPath()).thenReturn(new Path(hdfsPath));
        when(fileStatus.isFile()).thenReturn(true);
        when(fileStatus.isDirectory()).thenReturn(false);
        when(fileStatus.getOwner()).thenReturn("spiderman");

        String normalFile = CWD.getAbsolutePath() + "/src/test/resources/hdfs/normal_file.txt";
        FSDataInputStream fsDataInputStream = new FSDataInputStream(new MockDataInputStream(normalFile));
        when(fileSystem.rename(any(Path.class), any(Path.class))).thenReturn(true);
        when(fileSystem.open(any(Path.class))).thenReturn(fsDataInputStream);

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);

        underTest = new HdfsConsumer(endpoint, processor, endpointConfig, hdfsInfoFactory, new StringBuilder(hdfsPath));

        // when
        int actual = underTest.doPoll();

        // then
        assertThat(actual, is(1));
        verify(processor, times(1)).process(exchangeCaptor.capture());
        Exchange exchange = exchangeCaptor.getValue();
        assertThat(exchange, notNullValue());

        InputStream body = (InputStream) exchange.getIn().getBody();
        assertThat(body, notNullValue());
    }

}
