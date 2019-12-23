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

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Progressable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HdfsOutputStreamTest {

    private HdfsInfoFactory hdfsInfoFactory;
    private HdfsConfiguration endpointConfig;
    private FileSystem fileSystem;

    private HdfsOutputStream underTest;

    @Before
    public void setUp() throws Exception {
        hdfsInfoFactory = mock(HdfsInfoFactory.class);
        HdfsInfo hdfsInfo = mock(HdfsInfo.class);
        endpointConfig = mock(HdfsConfiguration.class);

        fileSystem = mock(FileSystem.class);
        Configuration configuration = mock(Configuration.class);
        Path path = mock(Path.class);

        when(hdfsInfoFactory.newHdfsInfo(anyString())).thenReturn(hdfsInfo);
        when(hdfsInfoFactory.newHdfsInfoWithoutAuth(anyString())).thenReturn(hdfsInfo);
        when(hdfsInfoFactory.getEndpointConfig()).thenReturn(endpointConfig);

        when(hdfsInfo.getFileSystem()).thenReturn(fileSystem);
        when(hdfsInfo.getConfiguration()).thenReturn(configuration);
        when(hdfsInfo.getPath()).thenReturn(path);
    }

    @Test
    public void createOutputStreamForExistingNormalFileWithAppend() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataOutputStream fsDataOutputStream = mock(FSDataOutputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(true);
        when(endpointConfig.isAppend()).thenReturn(false);

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataOutputStream);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);

        // when
        underTest = HdfsOutputStream.createOutputStream(hdfsPath, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
        verify(endpointConfig, times(1)).setAppend(true);
        verify(fileSystem, times(1)).rename(any(Path.class), pathCaptor.capture());
        assertThat(pathCaptor.getValue().toString(), is("hdfs://localhost/target/test/multiple-consumers.null"));
    }

    @Test
    public void createOutputStreamForMissingNormalFileWithAppend() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataOutputStream fsDataOutputStream = mock(FSDataOutputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(true);
        when(endpointConfig.isAppend()).thenReturn(false);

        when(fileSystem.exists(any(Path.class))).thenReturn(false);
        when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataOutputStream);

        // when
        underTest = HdfsOutputStream.createOutputStream(hdfsPath, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
        verify(endpointConfig, times(1)).setAppend(false);
        verify(fileSystem, times(0)).rename(any(Path.class), any(Path.class));
    }

    @Test
    public void createOutputStreamOverwriteExistingNormalFile() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataOutputStream fsDataOutputStream = mock(FSDataOutputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(false);
        when(endpointConfig.isAppend()).thenReturn(false);
        when(endpointConfig.isOverwrite()).thenReturn(true);

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataOutputStream);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);

        // when
        underTest = HdfsOutputStream.createOutputStream(hdfsPath, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
        verify(fileSystem, times(1)).delete(pathCaptor.capture(), eq(true));
        assertThat(pathCaptor.getValue().toString(), is(hdfsPath));

        assertThat(underTest.getNumOfWrittenBytes(), is(0L));
        assertThat(underTest.getNumOfWrittenMessages(), is(0L));
        assertThat(underTest.getActualPath(), is(hdfsPath));
        assertThat(underTest.getLastAccess() > 0L, is(true));
        assertThat(underTest.isBusy().get(), is(false));
    }

    @Test
    public void createOutputStreamWillFailForExistingNormalFileNoOverwrite() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataOutputStream fsDataOutputStream = mock(FSDataOutputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(false);
        when(endpointConfig.isAppend()).thenReturn(false);
        when(endpointConfig.isOverwrite()).thenReturn(false);

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataOutputStream);

        // when
        Throwable expected = null;
        try {
            underTest = HdfsOutputStream.createOutputStream(hdfsPath, hdfsInfoFactory);
        } catch (Exception e) {
            expected = e;
        }

        // then
        assertThat(expected, notNullValue());
        assertThat(expected.getMessage(), is("File [hdfs://localhost/target/test/multiple-consumers] already exists"));
    }

}
