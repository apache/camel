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
package org.apache.camel.component.azure.storage.datalake.integration;

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import org.apache.camel.CamelContext;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.test.infra.azure.common.AzureConfigs;
import org.apache.camel.test.infra.azure.common.services.AzureService;
import org.apache.camel.test.infra.azure.storage.datalake.clients.AzureStorageDataLakeClientUtils;
import org.apache.camel.test.infra.azure.storage.datalake.services.AzureStorageDataLakeServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

//@EnabledIfSystemProperty(named = "azure.instance.type", matches = "remote")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Base extends CamelTestSupport {

    @RegisterExtension
    public AzureService service = AzureStorageDataLakeServiceFactory.createService();

    protected DataLakeServiceClient serviceClient;
    protected DataLakeConfiguration configuration;
    protected String fileSystemName;

    static {
        initCredentials();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("serviceClient", serviceClient);
        return context;
    }

    static void initCredentials() {
        String accountName = System.getProperty("accountName");
        String accessKey = System.getProperty("accountKey");

        if (StringUtils.isNotEmpty(accountName) && StringUtils.isNotEmpty(accessKey)) {
            System.setProperty(AzureConfigs.ACCOUNT_NAME, accountName);
            System.setProperty(AzureConfigs.ACCOUNT_KEY, accessKey);
        }
    }

    @BeforeAll
    void initProperties() {
        fileSystemName = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);

        configuration = new DataLakeConfiguration();
        configuration.setSharedKeyCredential(new StorageSharedKeyCredential(
                service.azureCredentials().accountName(),
                service.azureCredentials().accountKey()));
        configuration.setFileSystemName(fileSystemName);

        final Set<OpenOption> openOptions = new HashSet<OpenOption>();
        openOptions.add(StandardOpenOption.WRITE);
        openOptions.add(StandardOpenOption.CREATE);
        openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);

        configuration.setOpenOptions(openOptions);

        serviceClient = AzureStorageDataLakeClientUtils.getClient();
    }
}
