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
package org.apache.camel.component.file.azure;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void hostForAccountURIShouldDefaultToFileCoreWindowsNetSuffix() {
        var remoteConf = context.getEndpoint("azure-files://account/share", FilesEndpoint.class).getConfiguration();
        assertEquals("account.file.core.windows.net", remoteConf.getHost());
    }

    @Test
    void shareForValidURIShouldBeExtracted() {
        var remoteConf = context.getEndpoint("azure-files://account/share?", FilesEndpoint.class).getConfiguration();
        assertEquals("share", remoteConf.getShare());
    }

    @Test
    void shareForValidURIShouldBeExtracted2() {
        var remoteConf = context.getEndpoint("azure-files://account/share/", FilesEndpoint.class).getConfiguration();
        assertEquals("share", remoteConf.getShare());
    }

    @Test
    void shareForDoubleSlashURIPathShouldBeExtracted() {
        // relaxed handling, it could be rejected if we wanted to be strict
        var remoteConf = context.getEndpoint("azure-files://account//share/", FilesEndpoint.class).getConfiguration();
        assertEquals("share", remoteConf.getShare());
    }

    @Test
    void shareForValidURIShouldBeExtracted3() {
        var remoteConf = context.getEndpoint("azure-files://account/share/?", FilesEndpoint.class).getConfiguration();
        assertEquals("share", remoteConf.getShare());
    }

    @Test
    void shareForValidURIShouldBeExtracted4() {
        var remoteConf = context.getEndpoint("azure-files://account/share/path", FilesEndpoint.class).getConfiguration();
        assertEquals("share", remoteConf.getShare());
    }

    @Test
    void directoryForValidShareURIShouldBeExtracted() {
        var remoteConf = context.getEndpoint("azure-files://account/share?", FilesEndpoint.class).getConfiguration();
        assertEquals("/", remoteConf.getDirectory());
        assertEquals("/", remoteConf.getDirectoryName());
    }

    @Test
    void dirForValidShareURIShouldStartWithSlash() {
        var remoteConf = context.getEndpoint("azure-files://account/share/?", FilesEndpoint.class).getConfiguration();
        assertEquals("/", remoteConf.getDirectory());
        assertEquals("/", remoteConf.getDirectoryName());
    }

    @Test
    void endpointURIPathWithDoubleSlashShouldThrowRuntimeException() {
        // it could be rejected or relaxed, we do not mind, rejecting is slightly is slightly cleaner
        // by observation ResolveEndpointFailedException but let's not rely on too concrete subtype
        assertThrows(RuntimeException.class,
                () -> context.getEndpoint("azure-files://account/share//?", FilesEndpoint.class).getConfiguration());
    }

    @Test
    void endpointURIPathWithSlashOnlyShouldThrowRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> context.getEndpoint("azure-files://account/?", FilesEndpoint.class).getConfiguration());
    }

    @Test
    void endpointURIPathWithDoubleSlashOnlyShouldThrowRuntimeException() {
        assertThrows(RuntimeException.class,
                () -> context.getEndpoint("azure-files://account//?", FilesEndpoint.class).getConfiguration());
    }

    @Test
    void dirForValidDirectoryNameURIShouldBeExtracted() {
        var remoteConf = context.getEndpoint("azure-files://account/share/dir", FilesEndpoint.class).getConfiguration();
        assertEquals("/dir", remoteConf.getDirectory());
        assertEquals("/dir", remoteConf.getDirectoryName());
    }

    @Test
    void dirForValidDirectoryNameURIShouldBeExtracted2() {
        var remoteConf = context.getEndpoint("azure-files://account/share/dir?", FilesEndpoint.class).getConfiguration();
        assertEquals("/dir", remoteConf.getDirectory());
        assertEquals("/dir", remoteConf.getDirectoryName());
    }

    @Test
    void dirForValidDirectoryNameURIShouldBeExtracted3() {
        var remoteConf = context.getEndpoint("azure-files://account/share/dir/?", FilesEndpoint.class).getConfiguration();
        assertEquals("/dir", remoteConf.getDirectory());
        assertEquals("/dir", remoteConf.getDirectoryName());
    }

}
