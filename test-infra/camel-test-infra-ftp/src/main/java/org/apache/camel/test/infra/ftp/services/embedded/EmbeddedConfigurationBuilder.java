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

public final class EmbeddedConfigurationBuilder {
    private final EmbeddedConfiguration embeddedConfiguration = new EmbeddedConfiguration();

    private EmbeddedConfigurationBuilder() {

    }

    public EmbeddedConfigurationBuilder withTestDirectory(String testDirectory) {
        embeddedConfiguration.setTestDirectory(testDirectory);

        return this;
    }

    public EmbeddedConfigurationBuilder addUser(EmbeddedConfiguration.User user) {
        embeddedConfiguration.addUser(user);

        return this;
    }

    public EmbeddedConfigurationBuilder addUser(
            String username, String password, EmbeddedConfiguration.User.UserInfo userInfo) {
        embeddedConfiguration.addUser(new EmbeddedConfiguration.User(username, password, userInfo));

        return this;
    }

    public EmbeddedConfigurationBuilder withAdmin(EmbeddedConfiguration.User adminUser) {
        embeddedConfiguration.setAdmin(adminUser);

        return this;
    }

    public EmbeddedConfigurationBuilder withAdmin(
            String username, String password, EmbeddedConfiguration.User.UserInfo userInfo) {
        embeddedConfiguration.setAdmin(username, password, userInfo);

        return this;
    }

    public EmbeddedConfigurationBuilder withServerAddress(String address) {
        embeddedConfiguration.setServerAddress(address);

        return this;
    }

    public EmbeddedConfigurationBuilder withKeyStore(String keyStore) {
        embeddedConfiguration.setKeyStore(keyStore);

        return this;
    }

    public EmbeddedConfigurationBuilder withKeyStorePassword(String keyStorePassword) {
        embeddedConfiguration.setKeyStorePassword(keyStorePassword);

        return this;
    }

    public EmbeddedConfigurationBuilder withKeyStoreType(String keyStoreType) {
        embeddedConfiguration.setKeyStoreType(keyStoreType);

        return this;
    }

    public EmbeddedConfigurationBuilder withKeyStoreAlgorithm(String keyStoreAlgorithm) {
        embeddedConfiguration.setKeyStoreAlgorithm(keyStoreAlgorithm);

        return this;
    }

    public EmbeddedConfigurationBuilder withKnownHosts(String knownHosts) {
        embeddedConfiguration.setKnownHosts(knownHosts);

        return this;
    }

    public EmbeddedConfigurationBuilder withKnownHostsPath(String knownHostsPath) {
        embeddedConfiguration.setKnownHostsPath(knownHostsPath);

        return this;
    }

    public EmbeddedConfigurationBuilder withKeyPairFile(String keyPairFile) {
        embeddedConfiguration.setKeyPairFile(keyPairFile);

        return this;
    }

    public EmbeddedConfigurationBuilder withSecurityConfiguration(boolean useImplicit, String authValue, boolean clientAuth) {
        return withSecurityConfiguration(new EmbeddedConfiguration.SecurityConfiguration(useImplicit, authValue, clientAuth));

    }

    public EmbeddedConfigurationBuilder withSecurityConfiguration(
            EmbeddedConfiguration.SecurityConfiguration securityConfiguration) {
        embeddedConfiguration.setSecurityConfiguration(securityConfiguration);

        return this;
    }

    public EmbeddedConfiguration build() {
        return embeddedConfiguration;
    }

    public static EmbeddedConfigurationBuilder defaultConfigurationTemplate() {
        final EmbeddedConfiguration.User.UserInfo writableUser = new EmbeddedConfiguration.User.UserInfo(null, true);
        final EmbeddedConfiguration.User.UserInfo nonWritableUser = new EmbeddedConfiguration.User.UserInfo(null, false);

        EmbeddedConfigurationBuilder builder = new EmbeddedConfigurationBuilder()
                .withTestDirectory("res/home")
                .addUser("admin", "admin", writableUser)
                .addUser("scott", "tiger", writableUser)
                .addUser("dummy", "foo", nonWritableUser)
                .addUser("us@r", "t%st", writableUser)
                .addUser("anonymous", null, nonWritableUser)
                .addUser("joe", "p+%w0&r)d", writableUser)
                .addUser("jane", "%j#7%c6i", writableUser)
                .withAdmin("admin", null, null)
                .withServerAddress("localhost");

        return builder;
    }

    public static EmbeddedConfiguration defaultConfiguration() {
        return defaultConfigurationTemplate().build();
    }

    public static EmbeddedConfigurationBuilder defaultFtpsConfigurationTemplate() {
        final EmbeddedConfiguration.User.UserInfo writableUser = new EmbeddedConfiguration.User.UserInfo(null, true);
        final EmbeddedConfiguration.User.UserInfo nonWritableUser = new EmbeddedConfiguration.User.UserInfo(null, false);

        EmbeddedConfigurationBuilder builder = new EmbeddedConfigurationBuilder()
                .withTestDirectory("res/home")
                .addUser("admin", "admin", writableUser)
                .addUser("scott", "tiger", writableUser)
                .addUser("dummy", "foo", nonWritableUser)
                .addUser("us@r", "t%st", writableUser)
                .addUser("anonymous", null, nonWritableUser)
                .addUser("joe", "p+%w0&r)d", writableUser)
                .addUser("jane", "%j#7%c6i", writableUser)
                .withAdmin("admin", null, null)
                .withServerAddress("localhost")
                .withKeyStore("./src/test/resources/server.jks")
                .withKeyStorePassword("password")
                .withKeyStoreType("JKS")
                .withKeyStoreAlgorithm("SunX509");

        return builder;
    }

    public static EmbeddedConfiguration defaultFtpsConfiguration() {
        return defaultFtpsConfigurationTemplate().build();
    }

    public static EmbeddedConfiguration defaultSftpConfiguration() {
        final EmbeddedConfiguration.User.UserInfo writableUser = new EmbeddedConfiguration.User.UserInfo(null, true);
        final EmbeddedConfiguration.User.UserInfo nonWritableUser = new EmbeddedConfiguration.User.UserInfo(null, false);

        EmbeddedConfigurationBuilder builder = new EmbeddedConfigurationBuilder()
                .withTestDirectory("res/home")
                .addUser("admin", "admin", writableUser)
                .addUser("scott", "tiger", writableUser)
                .addUser("dummy", "foo", nonWritableUser)
                .addUser("us@r", "t%st", writableUser)
                .addUser("anonymous", null, nonWritableUser)
                .addUser("joe", "p+%w0&r)d", writableUser)
                .addUser("jane", "%j#7%c6i", writableUser)
                .withAdmin("admin", null, null)
                .withServerAddress("localhost")
                .withKeyStore("./src/test/resources/server.jks")
                .withKeyStorePassword("password")
                .withKeyStoreType("JKS")
                .withKeyStoreAlgorithm("SunX509")
                .withKnownHosts(EmbeddedConfiguration.DEFAULT_KNOWN_HOSTS)
                .withKnownHostsPath("user-home/.ssh/known_hosts")
                .withKeyPairFile("src/test/resources/hostkey.pem");

        return builder.embeddedConfiguration;
    }
}
