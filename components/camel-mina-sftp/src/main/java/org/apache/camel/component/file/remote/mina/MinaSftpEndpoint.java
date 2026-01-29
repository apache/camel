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
package org.apache.camel.component.file.remote.mina;

import org.apache.camel.Category;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.component.file.remote.FtpConstants;
import org.apache.camel.component.file.remote.RemoteFileConsumer;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.component.file.remote.strategy.SftpProcessStrategyFactory;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * Upload and download files to/from SFTP servers using Apache MINA SSHD.
 */
@UriEndpoint(firstVersion = "4.18.0", scheme = "mina-sftp", extendsScheme = "file", title = "MINA SFTP",
             syntax = "mina-sftp:host:port/directoryName", category = { Category.FILE }, headersClass = FtpConstants.class)
@Metadata(excludeProperties = "appendChars,bufferSize,siteCommand,"
                              + "directoryMustExist,extendedAttributes,probeContentType,startingDirectoryMustExist,"
                              + "startingDirectoryMustHaveAccess,forceWrites,copyAndDeleteOnRenameFail,"
                              + "renameUsingCopy,synchronous")
public class MinaSftpEndpoint extends RemoteFileEndpoint<SftpRemoteFile> {

    @UriParam
    protected MinaSftpConfiguration configuration;

    public MinaSftpEndpoint() {
    }

    public MinaSftpEndpoint(String uri, MinaSftpComponent component, MinaSftpConfiguration configuration) {
        super(uri, component, configuration);
        this.configuration = configuration;
    }

    @Override
    public MinaSftpConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void setConfiguration(GenericFileConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("MinaSftpConfiguration expected");
        }
        // need to set on both
        this.configuration = (MinaSftpConfiguration) configuration;
        super.setConfiguration(configuration);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        validateConfiguration();
    }

    /**
     * Validates the endpoint configuration at startup.
     * <p/>
     * This provides early feedback for invalid configuration values rather than failing at runtime during file
     * operations.
     */
    private void validateConfiguration() {
        // Validate chmod value if set
        if (ObjectHelper.isNotEmpty(configuration.getChmod())) {
            validateOctalPermission(configuration.getChmod(), "chmod");
        }

        // Validate chmodDirectory value if set
        if (ObjectHelper.isNotEmpty(configuration.getChmodDirectory())) {
            validateOctalPermission(configuration.getChmodDirectory(), "chmodDirectory");
        }
    }

    /**
     * Validates that a permission string is a valid octal number for Unix file permissions.
     *
     * @param  value                    the permission string to validate (e.g., "644", "755")
     * @param  parameterName            the name of the parameter for error messages
     * @throws IllegalArgumentException if the value is not a valid octal permission
     */
    private void validateOctalPermission(String value, String parameterName) {
        try {
            int permissions = Integer.parseInt(value, 8);
            // Valid Unix permissions are 0-7777 (octal)
            // 7777 = special bits (setuid, setgid, sticky) + rwx for owner, group, others
            if (permissions < 0 || permissions > 07777) {
                throw new IllegalArgumentException(
                        String.format("Invalid %s value: '%s'. Must be an octal number between 000 and 7777 (e.g., 644, 755).",
                                parameterName, value));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid %s value: '%s'. Must be a valid octal number (e.g., 644, 755). "
                                  + "The value contains non-octal characters (valid: 0-7).",
                            parameterName, value),
                    e);
        }
    }

    @Override
    protected RemoteFileConsumer<SftpRemoteFile> buildConsumer(Processor processor) {
        return new MinaSftpConsumer(
                this, processor, createRemoteFileOperations(),
                processStrategy != null ? processStrategy : createGenericFileStrategy());
    }

    @Override
    protected GenericFileProducer<SftpRemoteFile> buildProducer() {
        if (this.getMoveExistingFileStrategy() == null) {
            this.setMoveExistingFileStrategy(createDefaultMoveExistingFileStrategy());
        }
        return new MinaSftpProducer(this, createRemoteFileOperations());
    }

    /**
     * Default Existing File Move Strategy
     *
     * @return the default implementation for mina-sftp component
     */
    private FileMoveExistingStrategy createDefaultMoveExistingFileStrategy() {
        return new MinaSftpDefaultMoveExistingFileStrategy();
    }

    @Override
    protected GenericFileProcessStrategy<SftpRemoteFile> createGenericFileStrategy() {
        return new SftpProcessStrategyFactory().createGenericFileProcessStrategy(getCamelContext(), getParamsAsMap());
    }

    @Override
    public RemoteFileOperations<SftpRemoteFile> createRemoteFileOperations() {
        MinaSftpOperations operations = new MinaSftpOperations();
        operations.setEndpoint(this);
        return operations;
    }

    @Override
    public String getScheme() {
        return "mina-sftp";
    }
}
