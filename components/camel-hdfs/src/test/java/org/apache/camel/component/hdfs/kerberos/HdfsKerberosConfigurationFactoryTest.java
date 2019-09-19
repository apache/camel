package org.apache.camel.component.hdfs.kerberos;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class HdfsKerberosConfigurationFactoryTest {

    @Test
    public void setupExistingKerberosConfigFile() throws IOException {
        // given
        String kerberosConfigFileLocation = null;

        // when
        HdfsKerberosConfigurationFactory.setKerberosConfigFile(kerberosConfigFileLocation);

        // then

    }



}