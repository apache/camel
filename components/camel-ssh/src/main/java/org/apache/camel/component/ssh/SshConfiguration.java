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

import java.net.URI;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.StringHelper;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.keyprovider.KeyPairProvider;

@UriParams
public class SshConfiguration implements Cloneable {
    public static final int DEFAULT_SSH_PORT = 22;

    @UriPath(description = "The host name or IP address of the SSH server. When the .ssh/config file in the home"
                           + " directory of the user has an entry for this host, the client connects to the HostName of"
                           + " that entry.")
    @Metadata(required = true)
    private String host;
    @UriPath(defaultValue = "" + DEFAULT_SSH_PORT, description = "The port of the SSH server.")
    private int port = DEFAULT_SSH_PORT;
    @UriParam(label = "security", security = "secret",
              description = "The user name to log in with. The CamelSshUsername header replaces it for the producer.")
    private String username;
    @UriParam(label = "security", security = "secret",
              description = "The password for password authentication, which is used only when neither certResource nor"
                            + " keyPairProvider is set. The CamelSshPassword header replaces it for the producer.")
    private String password;
    @UriParam(label = "consumer",
              description = "The command that the consumer runs on the SSH server at each poll. The consumer needs it, as"
                            + " each poll fails without it. Some servers need the command to end with a newline, which is"
                            + " written %0A in an endpoint URI.")
    private String pollCommand;
    @UriParam(label = "security",
              description = "The org.apache.sshd.common.keyprovider.KeyPairProvider that gives the key pair for public key"
                            + " authentication. It is not used when certResource is set, and when it is used, the password"
                            + " option and the CamelSshPassword header are not.")
    private KeyPairProvider keyPairProvider;
    @UriParam(label = "security",
              description = "The key type of the key pair to use from certResource or keyPairProvider, for example ssh-rsa,"
                            + " ssh-ed25519 or ecdsa-sha2-nistp256. If not set, the first key pair is used.")
    private String keyType;
    @UriParam(label = "security",
              description = "The resource with the private key for public key authentication, in OpenSSH or PEM format, for"
                            + " example classpath:id_ed25519 or file:/home/camel/.ssh/id_ed25519. When set, it is used instead"
                            + " of keyPairProvider, and the password option and the CamelSshPassword header are not used.")
    private String certResource;
    @UriParam(label = "security", security = "secret",
              description = "The passphrase of the private key in certResource, when that key is encrypted.")
    private String certResourcePassword;
    @UriParam(defaultValue = "30000",
              description = "The time in milliseconds that the component waits for each step of setting up the session:"
                            + " the connection, the authentication and the opening of the channel. It does not limit how long"
                            + " the command runs.")
    private long timeout = 30000;
    @UriParam(label = "security", security = "insecure:ssl",
              description = "The resource of a file in the OpenSSH known_hosts format, such as file:/etc/camel/known_hosts,"
                            + " that the server host key is verified against. It is read at each connection, and a resource"
                            + " or a line that cannot be read makes every connection fail. When not set, the server host key"
                            + " is not verified and any server is accepted, unless clientBuilder sets a ServerKeyVerifier,"
                            + " which this option replaces when it is set.")
    private String knownHostsResource;
    @UriParam(label = "security", defaultValue = "false", security = "insecure:ssl", insecureValue = "false",
              description = "Whether to reject a server whose host has no entry without a marker in knownHostsResource. When"
                            + " false, such a server is accepted and a warning is logged. A server whose host has such entries"
                            + " is always rejected when none of them lists the key it presents, and so is a key listed in a"
                            + " matching entry with the revoked marker. This option has no effect when knownHostsResource is not set.")
    private boolean failOnUnknownHost;
    @UriParam(label = "advanced", defaultValue = Channel.CHANNEL_EXEC,
              enums = "exec,shell",
              description = "The type of SSH channel that runs the command: exec runs it as a single command, and shell writes"
                            + " it to an interactive shell and reads the output up to the shellPrompt text. The endpoint fails"
                            + " to start with another value.")
    private String channelType = Channel.CHANNEL_EXEC;
    @UriParam(label = "advanced",
              description = "The text that the shell prompt ends with, which channelType=shell needs: the endpoint fails to"
                            + " start without it. The component reads the output of the shell until it ends with this text,"
                            + " before and after it writes the command, and the message body is the text between the first"
                            + " and the last line break of that output.")
    private String shellPrompt;
    @UriParam(label = "advanced", defaultValue = "100",
              description = "The time in milliseconds between two reads of the shell output while the component waits for the"
                            + " shellPrompt text, with channelType=shell.")
    private long sleepForShellPrompt = 100;
    @UriParam(label = "security",
              description = "A comma-separated list of the key exchange algorithms that the client proposes, in order of"
                            + " preference, for example mlkem768x25519-sha256,curve25519-sha256. It replaces the default list"
                            + " of Apache MINA SSHD, which already proposes the post-quantum hybrid algorithms. Names that are"
                            + " not known are skipped with a warning.")
    private String kex;
    @UriParam(label = "security",
              description = "A comma-separated list of the ciphers that the client proposes, in order of preference, for"
                            + " example aes256-ctr,aes128-ctr. It replaces the default list of Apache MINA SSHD."
                            + " Names that are not known are skipped with a warning.")
    private String ciphers;
    @UriParam(label = "security",
              description = "A comma-separated list of the message authentication code (MAC) algorithms that the client"
                            + " proposes, in order of preference, for example hmac-sha2-512,hmac-sha2-256. It"
                            + " replaces the default list of Apache MINA SSHD. Names that are not known are skipped with a"
                            + " warning.")
    private String macs;
    @UriParam(label = "security",
              description = "A comma-separated list of the signature algorithms that the client supports, in order of"
                            + " preference, for example ssh-ed25519,rsa-sha2-512. It replaces the default list of Apache MINA"
                            + " SSHD. Names that are not known are skipped with a warning.")
    private String signatures;
    @UriParam(label = "advanced",
              description = "A comma-separated list of the compression algorithms that the client proposes, in order of"
                            + " preference, for example zlib,none. If not set, the client uses no compression.")
    private String compressions;
    @UriParam(label = "advanced", defaultValue = "600000",
              description = "The time in milliseconds after which Apache MINA SSHD closes a session without traffic. The"
                            + " session is open while a command runs. The default, 10 minutes, is the default of Apache MINA"
                            + " SSHD. If 0 or less, the session has no idle timeout.")
    private long idleTimeout = 600000;
    @UriParam(label = "advanced",
              description = "The interval in milliseconds at which the client sends keep-alive requests to the server while a"
                            + " command runs. If 0 or less, no keep-alive requests are sent.")
    private long heartbeatInterval;
    @UriParam(label = "advanced",
              description = "The number of keep-alive requests without reply after which the client closes the session, when"
                            + " heartbeatInterval is set. If 0 or less, the default of Apache MINA SSHD applies.")
    private int heartbeatReplyMaxWait;
    @UriParam(label = "advanced",
              description = "The time in milliseconds that Apache MINA SSHD allows for the authentication before it closes the"
                            + " session. If 0 or less, its default of 2 minutes applies. The timeout option also limits how"
                            + " long the component waits for the authentication.")
    private long authTimeout;
    @UriParam(label = "advanced",
              description = "The time in milliseconds that the socket connection to the server may take. If 0 or less, the"
                            + " default of Apache MINA SSHD, 1 minute, applies. The timeout option also limits how long the"
                            + " component waits for the connection.")
    private long connectTimeout;
    @UriParam(label = "advanced",
              description = "The time in milliseconds that the component waits for the channel that runs the command to"
                            + " open. If 0 or less, the timeout option applies.")
    private long channelOpenTimeout;
    @UriParam(description = "To use a custom org.apache.sshd.client.ClientBuilder to create the SSH client, for example to"
                            + " set a ServerKeyVerifier. The producer and the consumer each create a client with it when they"
                            + " start, and the options of this endpoint that are set are applied over its settings.")
    @Metadata(label = "advanced", autowired = true)
    private ClientBuilder clientBuilder;

    public SshConfiguration() {
    }

    public SshConfiguration(URI uri) {
        configure(uri);
    }

    public void configure(URI uri) {
        // UserInfo can contain both username and password as: user:pwd@sshserver
        // see: http://en.wikipedia.org/wiki/URI_scheme
        String username = uri.getUserInfo();
        String pw = null;
        if (username != null && username.contains(":")) {
            pw = StringHelper.after(username, ":");
            username = StringHelper.before(username, ":");
        }
        if (username != null) {
            setUsername(username);
        }
        if (pw != null) {
            setPassword(pw);
        }

        if (getHost() == null && uri.getHost() != null) {
            setHost(uri.getHost());
        }

        // URI.getPort returns -1 if port not defined, else use default port
        int uriPort = uri.getPort();
        if (getPort() == DEFAULT_SSH_PORT && uriPort != -1) {
            setPort(uriPort);
        }
    }

    public SshConfiguration copy() {
        try {
            return (SshConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPollCommand() {
        return pollCommand;
    }

    public void setPollCommand(String pollCommand) {
        this.pollCommand = pollCommand;
    }

    public KeyPairProvider getKeyPairProvider() {
        return keyPairProvider;
    }

    public void setKeyPairProvider(KeyPairProvider keyPairProvider) {
        this.keyPairProvider = keyPairProvider;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getCertResource() {
        return certResource;
    }

    public void setCertResource(String certResource) {
        this.certResource = certResource;
    }

    public String getCertResourcePassword() {
        return certResourcePassword;
    }

    public void setCertResourcePassword(String certResourcePassword) {
        this.certResourcePassword = certResourcePassword;
    }

    public String getKnownHostsResource() {
        return knownHostsResource;
    }

    public void setKnownHostsResource(String knownHostsResource) {
        this.knownHostsResource = knownHostsResource;
    }

    public boolean isFailOnUnknownHost() {
        return failOnUnknownHost;
    }

    public void setFailOnUnknownHost(boolean failOnUnknownHost) {
        this.failOnUnknownHost = failOnUnknownHost;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getShellPrompt() {
        return shellPrompt;
    }

    public void setShellPrompt(String shellPrompt) {
        this.shellPrompt = shellPrompt;
    }

    public long getSleepForShellPrompt() {
        return sleepForShellPrompt;
    }

    public void setSleepForShellPrompt(long sleepForShellPrompt) {
        this.sleepForShellPrompt = sleepForShellPrompt;
    }

    public String getKex() {
        return kex;
    }

    public void setKex(String kex) {
        this.kex = kex;
    }

    public String getCiphers() {
        return ciphers;
    }

    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }

    public String getMacs() {
        return macs;
    }

    public void setMacs(String macs) {
        this.macs = macs;
    }

    public String getSignatures() {
        return signatures;
    }

    public void setSignatures(String signatures) {
        this.signatures = signatures;
    }

    public String getCompressions() {
        return compressions;
    }

    public void setCompressions(String compressions) {
        this.compressions = compressions;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getHeartbeatReplyMaxWait() {
        return heartbeatReplyMaxWait;
    }

    public void setHeartbeatReplyMaxWait(int heartbeatReplyMaxWait) {
        this.heartbeatReplyMaxWait = heartbeatReplyMaxWait;
    }

    public long getAuthTimeout() {
        return authTimeout;
    }

    public void setAuthTimeout(long authTimeout) {
        this.authTimeout = authTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getChannelOpenTimeout() {
        return channelOpenTimeout;
    }

    public void setChannelOpenTimeout(long channelOpenTimeout) {
        this.channelOpenTimeout = channelOpenTimeout;
    }

    public ClientBuilder getClientBuilder() {
        return clientBuilder;
    }

    public void setClientBuilder(ClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }
}
