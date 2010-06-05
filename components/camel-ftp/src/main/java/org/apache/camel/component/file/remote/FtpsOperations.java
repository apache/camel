/**
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
package org.apache.camel.component.file.remote;

import java.io.IOException;
import javax.net.ssl.SSLException;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPSClient;

/**
 * FTP Secure (FTP over SSL/TLS) operations
 *
 * @version $Revision$
 */
public class FtpsOperations extends FtpOperations {

    public FtpsOperations(FTPSClient client, FTPClientConfig clientConfig) {
        super(client, clientConfig);
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration) throws GenericFileOperationFailedException {
        boolean answer = super.connect(configuration);

        FtpsConfiguration config = (FtpsConfiguration) configuration;
        if (answer && config.isUseSecureDataChannel()) {
            try {
                // execProt must be configured
                ObjectHelper.notEmpty(config.getExecProt(), "execProt", config);

                if (log.isDebugEnabled()) {
                    log.debug("Secure data channel being initialized with execPbsz=" + config.getExecPbsz() + ", execPort=" + config.getExecProt());
                }
                if (config.getExecPbsz() != null) {
                    getFtpClient().execPBSZ(config.getExecPbsz());
                }
                getFtpClient().execPROT(config.getExecProt());
            } catch (SSLException e) {
                throw new GenericFileOperationFailedException(client.getReplyCode(),
                        client.getReplyString(), e.getMessage(), e);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(client.getReplyCode(),
                        client.getReplyString(), e.getMessage(), e);
            }
        }

        return answer;
    }

    @Override
    protected FTPSClient getFtpClient() {
        return (FTPSClient) super.getFtpClient();
    }

}
