package org.apache.camel.component.file.azure;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FilesConfigurationTests extends CamelTestSupport {

    @Test
    void accountForAccountHostURIShouldBeExtracted() {
        var remoteConf = context
                .getEndpoint("azure-files://account.file.core.windows.net/share", FilesEndpoint.class)
                .getConfiguration();
        assertEquals("account", remoteConf.getAccount());
    }

    @Test
    void accountForAccountOnlyURIShouldBeExtracted() {
        var remoteConf = context.getEndpoint("azure-files://account/share", FilesEndpoint.class).getConfiguration();
        assertEquals("account", remoteConf.getAccount());
    }

    @Test
    void hostForAccountHostURIShouldBeExtracted() {
        var remoteConf = context
                .getEndpoint("azure-files://account.file.core.windows.net/share", FilesEndpoint.class)
                .getConfiguration();
        assertEquals("account.file.core.windows.net", remoteConf.getHost());
    }

    @Test
    void hostForAccountURIShouldDefaultTo_file_core_windows_netSuffix() {
        var remoteConf = context.getEndpoint("azure-files://account/share", FilesEndpoint.class).getConfiguration();
        assertEquals("account.file.core.windows.net", remoteConf.getHost());
    }

}
