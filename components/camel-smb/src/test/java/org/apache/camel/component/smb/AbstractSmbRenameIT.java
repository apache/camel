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
package org.apache.camel.component.smb;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for testing SMB file rename operations with different configuration options. This class provides
 * a common test infrastructure for verifying the behavior of the two rename-related configuration parameters:
 * {@code renameUsingCopy} - When true (default), always uses copy+delete strategy {@code copyAndDeleteOnRenameFail} -
 * When true, falls back to copy+delete if atomic rename fails
 *
 */
public abstract class AbstractSmbRenameIT extends SmbServerTestSupport {

    @BeforeEach
    public void setItUp() {
        template.sendBodyAndHeader(getSmbUrl(), "Hello World", Exchange.FILE_NAME, getFilename());
    }

    /**
     * Provides the SMB URL with specific rename configuration options for testing.
     *
     * @return the SMB endpoint URL with query parameters for rename behavior
     */
    protected abstract String getSmbUrl();

    /**
     * Creates custom SMB operations for testing specific rename scenarios.
     *
     * Implementations can override specific methods (like {@code atomicRenameFile} or
     * {@code copyAndDeleteRenameStrategy}) to verify that the correct strategy is being used.
     *
     * @param  configuration the SMB configuration
     * @return               custom SmbOperations instance for testing
     */
    protected abstract SmbOperations createCustomSmbOperation(SmbConfiguration configuration);

    @Test
    public void testCopyAndDeleteOnRenameFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // Verify file was moved
        await().atMost(6, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World\n",
                        service.smbFile(getPath() + "/.done/" + getFilename())));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // Replace the SMB component with a custom one
        context.removeComponent("smb");
        context.addComponent("smb", new CustomSmbComponent(context));
        return context;
    }

    @Component("smb")
    class CustomSmbComponent extends SmbComponent {
        public CustomSmbComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected GenericFileEndpoint<FileIdBothDirectoryInformation> buildFileEndpoint(
                String uri, String remaining, Map<String, Object> parameters)
                throws Exception {

            // Replicate parent logic to build configuration
            String baseUri = getBaseUri(uri);
            SmbConfiguration config = new SmbConfiguration(new java.net.URI(baseUri));

            // Handle backwards compatible path parameter
            String path = getAndRemoveParameter(parameters, "path", String.class);
            if (path != null) {
                config.setPath(path);
            }

            if (config.getShareName() == null) {
                throw new IllegalArgumentException("ShareName must be configured");
            }

            // Create our custom endpoint instead of the default one
            SmbAtomicRenameBehaviorIT.CustomSmbEndpoint endpoint
                    = new SmbAtomicRenameBehaviorIT.CustomSmbEndpoint(uri, this, config);

            // Set properties on the endpoint (this consumes parameters from the map)
            setProperties(endpoint, parameters);

            return endpoint;
        }
    }

    @UriEndpoint(firstVersion = "4.3.0", scheme = "smb", title = "SMB", syntax = "smb:hostname:port/shareName/path",
                 headersClass = SmbConstants.class, category = { Category.FILE })
    @Metadata(excludeProperties = "appendChars,readLockIdempotentReleaseAsync,readLockIdempotentReleaseAsyncPoolSize,"
                                  + "readLockIdempotentReleaseDelay,readLockIdempotentReleaseExecutorService,"
                                  + "directoryMustExist,extendedAttributes,probeContentType,"
                                  + "startingDirectoryMustHaveAccess,chmodDirectory,forceWrites,"
                                  + "synchronous")
    class CustomSmbEndpoint extends SmbEndpoint {

        @UriParam
        private SmbConfiguration configuration;

        public CustomSmbEndpoint(String uri, CustomSmbComponent component, SmbConfiguration config) {
            super(uri, component, config);
        }

        @Override
        public GenericFileOperations<FileIdBothDirectoryInformation> createOperations() {
            SmbOperations operations = createCustomSmbOperation(getConfiguration());
            operations.setEndpoint(this);
            return operations;
        }
    }

    protected abstract String getFilename();

    protected abstract String getPath();

}
