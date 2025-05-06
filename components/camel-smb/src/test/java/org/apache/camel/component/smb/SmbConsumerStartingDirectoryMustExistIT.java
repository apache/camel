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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SmbConsumerStartingDirectoryMustExistIT extends SmbServerTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private String getSbmUrl() {
        return String.format(
                "smb:%s/%s/doesnotexist?username=%s&password=%s&delete=true&initialDelay=3000&autoCreate=false&startingDirectoryMustExist=true",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testStartingDirectoryMustExist() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getSbmUrl()).to("mock:result");
            }
        });
        try {
            context.start();
            Assertions.fail();
        } catch (GenericFileOperationFailedException e) {
            Assertions.assertEquals("Starting directory does not exist: doesnotexist", e.getMessage());
        }
    }

}
