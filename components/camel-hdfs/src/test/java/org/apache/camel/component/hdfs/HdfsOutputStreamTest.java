package org.apache.camel.component.hdfs;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HdfsOutputStreamTest {

    private HdfsInfoFactory hdfsInfoFactory;
    private HdfsInfo hdfsInfo;
    private FileSystem fileSystem;
    private Configuration configuration;
    private Path path;

    private HdfsOutputStream underTest;

    @Before
    public void setUp() throws Exception {
        hdfsInfoFactory = mock(HdfsInfoFactory.class);
        hdfsInfo = mock(HdfsInfo.class);
        fileSystem = mock(FileSystem.class);
        configuration = mock(Configuration.class);
        path = mock(Path.class);

        when(hdfsInfoFactory.newHdfsInfoWithoutAuth(anyString())).thenReturn(hdfsInfo);

        when(hdfsInfo.getFileSystem()).thenReturn(fileSystem);
        when(hdfsInfo.getConfiguration()).thenReturn(configuration);
        when(hdfsInfo.getPath()).thenReturn(path);
    }

    @Test
    public void createOutputStreamForNormalFile() throws IOException {
        // given
        String hdfsPath = "hdfs://localhost/target/test/multiple-consumers";
        HdfsConfiguration configuration = mock(HdfsConfiguration.class);
        when(configuration.getFileType()).thenReturn(HdfsFileType.NORMAL_FILE);

        // when
        underTest = HdfsOutputStream.createOutputStream(hdfsPath, configuration, hdfsInfoFactory);

        // then
        assertThat(underTest, notNullValue());
    }
}