package org.apache.camel.component.hdfs.kerberos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;

import static java.lang.String.format;

public class HdfsKerberosConfigurationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HdfsKerberosConfigurationFactory.class);

    private static final String KERBEROS_5_SYS_ENV = "java.security.krb5.conf";

    public static void setKerberosConfigFile(String kerberosConfigFileLocation) throws FileNotFoundException {
        if (!new File(kerberosConfigFileLocation).exists()) {
            throw new FileNotFoundException(format("KeyTab file [%s] could not be found.", kerberosConfigFileLocation));
        }

        String krb5Conf = System.getProperty(KERBEROS_5_SYS_ENV);
        if (krb5Conf == null || !krb5Conf.isEmpty()) {
            System.setProperty(KERBEROS_5_SYS_ENV, kerberosConfigFileLocation);
        } else if (!krb5Conf.equalsIgnoreCase(kerberosConfigFileLocation)) {
            LOGGER.warn("{} was already configured with: {}", KERBEROS_5_SYS_ENV, krb5Conf);
        }
    }

}
