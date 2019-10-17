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
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.booleanThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HdfsInputStreamTest {

    private HdfsInfoFactory hdfsInfoFactory;
    private HdfsConfiguration endpointConfig;
    private FileSystem fileSystem;
    private Configuration configuration;

    private HdfsInputStream underTest;

    @Before
    public void setUp() throws Exception {
        hdfsInfoFactory = mock(HdfsInfoFactory.class);
        HdfsInfo hdfsInfo = mock(HdfsInfo.class);
        endpointConfig = mock(HdfsConfiguration.class);

        fileSystem = mock(FileSystem.class);
        configuration = mock(Configuration.class);
        Path path = mock(Path.class);

        when(hdfsInfoFactory.newHdfsInfo(anyString())).thenReturn(hdfsInfo);
        when(hdfsInfoFactory.newHdfsInfoWithoutAuth(anyString())).thenReturn(hdfsInfo);
        when(hdfsInfoFactory.getEndpointConfig()).thenReturn(endpointConfig);

        when(hdfsInfo.getFileSystem()).thenReturn(fileSystem);
        when(hdfsInfo.getConfiguration()).thenReturn(configuration);
        when(hdfsInfo.getPath()).thenReturn(path);
    }

    @Test
    public void createInputStreamForLocalNormalFile() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataInputStream fsDataInputStream = mock(FSDataInputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.getFileSystemType()).thenReturn(HdfsFileSystemType.LOCAL);

        when(fileSystem.rename(any(Path.class), any(Path.class))).thenReturn(true);
        when(fileSystem.open(any(Path.class))).thenReturn(fsDataInputStream);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);

        // when
        underTest = HdfsInputStream.createInputStream(hdfsPath, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
        verify(endpointConfig, times(1)).setAppend(true);
        verify(fileSystem, times(1)).rename(any(Path.class), pathCaptor.capture());
        assertThat(pathCaptor.getValue().toString(), is("hdfs://localhost/target/test/multiple-consumers.null"));
    }

    @Test
    public void createInputStreamForMissingNormalFileWithAppend() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataInputStream fsDataInputStream = mock(FSDataInputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(true);
        when(endpointConfig.isAppend()).thenReturn(false);

        when(fileSystem.exists(any(Path.class))).thenReturn(false);
        //when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataInputStream);

        // when
        underTest = HdfsInputStream.createInputStream(hdfsPath, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
        verify(endpointConfig, times(1)).setAppend(false);
        verify(fileSystem, times(0)).rename(any(Path.class), any(Path.class));
    }

    @Test
    public void createInputStreamOverwriteExistingNormalFile() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataInputStream fsDataInputStream = mock(FSDataInputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(false);
        when(endpointConfig.isAppend()).thenReturn(false);
        when(endpointConfig.isOverwrite()).thenReturn(true);

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        //when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataInputStream);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);

        // when
        underTest = HdfsInputStream.createInputStream(hdfsPath, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
        verify(fileSystem, times(1)).delete(pathCaptor.capture(), booleanThat(is(true)));
        assertThat(pathCaptor.getValue().toString(), is(hdfsPath));

        assertThat(underTest.getNumOfReadBytes(), is(0L));
        assertThat(underTest.getActualPath(), is(hdfsPath));
    }

    @Test
    public void createInputStreamWillFailForExistingNormalFileNoOverwrite() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        FSDataInputStream fsDataInputStream = mock(FSDataInputStream.class);
        when(endpointConfig.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);
        when(endpointConfig.isWantAppend()).thenReturn(false);
        when(endpointConfig.isAppend()).thenReturn(false);
        when(endpointConfig.isOverwrite()).thenReturn(false);

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        //when(fileSystem.create(any(Path.class), anyBoolean(), anyInt(), anyShort(), anyLong(), any(Progressable.class))).thenReturn(fsDataInputStream);

        // when
        Throwable expected = null;
        try {
            underTest = HdfsInputStream.createInputStream(hdfsPath, hdfsInfoFactory);
        } catch (Exception e) {
            expected = e;
        }

        // then
        assertThat(expected, notNullValue());
        assertThat(expected.getMessage(), is("File [hdfs://localhost/target/test/multiple-consumers] already exists"));
    }

}
