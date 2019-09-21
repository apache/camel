package org.apache.camel.component.hdfs.kerberos;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

public class HdfsKerberosConfigurationFactoryTest {

    @Test(expected = FileNotFoundException.class)
    public void setupExistingKerberosConfigFileWithMissingConfigFile() throws IOException {
        // given
        String kerberosConfigFileLocation = "missing.conf";

        // when
        HdfsKerberosConfigurationFactory.setKerberosConfigFile(kerberosConfigFileLocation);

        // then
        /* exception was thrown */
    }



}