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

package org.apache.camel.test.infra.smb.services;

import org.apache.camel.test.infra.smb.common.SmbProperties;

public class SmbRemoteService implements SmbService {
    @Override
    public String address() {
        return System.getProperty(SmbProperties.SERVICE_ADDRESS);
    }

    @Override
    public String shareName() {
        return System.getProperty(SmbProperties.SHARE_NAME);
    }

    @Override
    public String userName() {
        return System.getProperty(SmbProperties.SMB_USERNAME);
    }

    @Override
    public String password() {
        return System.getProperty(SmbProperties.SMB_PASSWORD);
    }

    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {

    }

    @Override
    public void shutdown() {

    }
}
