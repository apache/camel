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
package org.apache.camel.component.ssh;

import org.apache.camel.spi.Metadata;

public interface SshConstants {

    @Metadata(label = "producer",
              description = "The user name that the producer logs in with, instead of the username option. Used only"
                            + " when allowUsernameAndPasswordFromHeaders=true; otherwise ignored. It applies to every kind"
                            + " of authentication: with certResource or keyPairProvider, the configured key logs in as"
                            + " this user.",
              javaType = "String")
    String USERNAME_HEADER = "CamelSshUsername";
    @Metadata(label = "producer",
              description = "The password that the producer uses for password authentication, instead of the password"
                            + " option. Used only when allowUsernameAndPasswordFromHeaders=true; otherwise ignored. It is"
                            + " not used when certResource or keyPairProvider is set.",
              javaType = "String", secret = true)
    String PASSWORD_HEADER = "CamelSshPassword";
    @Metadata(description = "The standard error of the command, as an InputStream.", javaType = "InputStream")
    String STDERR = "CamelSshStderr";
    @Metadata(description = "The exit status of the command. By convention, a value other than 0 means that the command"
                            + " failed, but the exchange does not fail for it. With channelType=shell it is usually null, as"
                            + " the shell is still running when the output is read.",
              javaType = "Integer", important = true)
    String EXIT_VALUE = "CamelSshExitValue";
}
