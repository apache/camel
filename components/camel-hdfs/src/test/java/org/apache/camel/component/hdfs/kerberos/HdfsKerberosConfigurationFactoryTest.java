package org.apache.camel.component.hdfs.kerberos;

import org.junit.Test;

import java.io.IOException;

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