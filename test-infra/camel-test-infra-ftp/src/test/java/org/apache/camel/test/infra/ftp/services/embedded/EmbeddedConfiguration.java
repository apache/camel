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

package org.apache.camel.test.infra.ftp.services.embedded;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmbeddedConfiguration {
    public static class User {
        public static class UserInfo {
            private final String home;
            private final boolean writePermission;

            public UserInfo(String home, boolean writePermission) {
                this.home = home;
                this.writePermission = writePermission;
            }

            public String getHome() {
                return home;
            }

            public boolean isWritePermission() {
                return writePermission;
            }
        }

        private final String username;
        private final String password;
        private final UserInfo userInfo;

        public User(String username, String password, UserInfo userInfo) {
            this.username = username;
            this.password = password;
            this.userInfo = userInfo;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public UserInfo getUserInfo() {
            return userInfo;
        }
    }

    public static class SecurityConfiguration {
        private final boolean useImplicit;
        private final String authValue;
        private final boolean clientAuth;

        public SecurityConfiguration(boolean useImplicit, String authValue, boolean clientAuth) {
            this.useImplicit = useImplicit;
            this.authValue = authValue;
            this.clientAuth = clientAuth;
        }

        public boolean isUseImplicit() {
            return useImplicit;
        }

        public String getAuthValue() {
            return authValue;
        }

        public boolean isClientAuth() {
            return clientAuth;
        }
    }

    static final String DEFAULT_KNOWN_HOSTS = "[localhost]:%d ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDdfIWeSV4o68dRrKS"
                                              + "zFd/Bk51E65UTmmSrmW0O1ohtzi6HzsDPjXgCtlTt3FqTcfFfI92IlTr4JWqC9UK1QT1ZTeng0MkPQmv68hDANHbt5CpETZHjW5q4OOgWhV"
                                              + "vj5IyOC2NZHtKlJBkdsMAa15ouOOJLzBvAvbqOR/yUROsEiQ==";

    private String testDirectory;
    private List<User> users = new ArrayList<>();
    private User admin;
    private String serverAddress;
    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType;
    private String keyStoreAlgorithm;
    private String knownHosts;
    private String knownHostsPath;
    private String keyPairFile;
    private SecurityConfiguration securityConfiguration;

    public String getTestDirectory() {
        return testDirectory;
    }

    void setTestDirectory(String testDirectory) {
        this.testDirectory = testDirectory;
    }

    void addUser(User user) {
        users.add(user);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public User getAdmin() {
        return admin;
    }

    void setAdmin(User admin) {
        this.admin = admin;
    }

    void setAdmin(String username, String password, User.UserInfo userInfo) {
        this.admin = new User(username, password, userInfo);
    }

    public String getServerAddress() {
        return serverAddress;
    }

    void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getKeyStore() {
        return keyStore;
    }

    void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreAlgorithm() {
        return keyStoreAlgorithm;
    }

    public void setKeyStoreAlgorithm(String keyStoreAlgorithm) {
        this.keyStoreAlgorithm = keyStoreAlgorithm;
    }

    public String getKnownHosts() {
        return knownHosts;
    }

    void setKnownHosts(String knownHosts) {
        this.knownHosts = knownHosts;
    }

    public String getKnownHostsPath() {
        return knownHostsPath;
    }

    void setKnownHostsPath(String knownHostsPath) {
        this.knownHostsPath = knownHostsPath;
    }

    public String getKeyPairFile() {
        return keyPairFile;
    }

    void setKeyPairFile(String keyPairFile) {
        this.keyPairFile = keyPairFile;
    }

    public SecurityConfiguration getSecurityConfiguration() {
        return securityConfiguration;
    }

    public void setSecurityConfiguration(SecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }
}
