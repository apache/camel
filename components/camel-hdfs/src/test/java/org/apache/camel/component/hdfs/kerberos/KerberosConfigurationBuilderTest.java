package org.apache.camel.component.hdfs.kerberos;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import static org.junit.Assert.*;

public class KerberosConfigurationBuilderTest {

    @Test
    public void withKerberosConfiguration() {
        // given
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/test-kerb5.conf";

        // when
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);

        // then

    }

    @Test
    public void setKerberosConfigFileWithRealFile() {
        // given
        String kerb5FileName = "test-kerb5.conf";
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/" + kerb5FileName;

        // when
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);

        // then
        String actual = System.getProperty("java.security.krb5.conf");
        assertNotNull(actual);
        assertTrue(actual.endsWith(kerb5FileName));
    }

    @Test
    public void setKerberosConfigFileWithMissingFile() {
        // given
        String kerb5FileName = "missing-kerb5.conf";
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/" + kerb5FileName;

        // when
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);

        // then
        String actual = System.getProperty("java.security.krb5.conf");
        assertNull(actual);
    }

    private String pwd() {
        return new File(".").getAbsolutePath();
    }

}