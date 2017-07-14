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
package org.apache.camel.component.consul;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.SessionCreatedResponse;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Registry;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.SerializationUtils;

/**
 * Apache Camel Plug-in for Consul Registry (Objects stored under kv/key as well
 * as bookmarked under kv/[type]/key to avoid iteration over types)
 */
public class ConsulRegistry implements Registry {

    private String hostname = "localhost";
    private int port = 8500;
    private Consul consul;
    private KeyValueClient kvClient;

    /* constructor with default port */
    public ConsulRegistry(String hostname) {
        this(hostname, 8500);
    }

    /* constructor (since spring.xml does not support builder pattern) */
    public ConsulRegistry(String hostname, int port) {
        super();
        this.hostname = hostname;
        this.port = port;
        HostAndPort hostAndPort = HostAndPort.fromParts(hostname, port);
        this.consul = Consul.builder().withHostAndPort(hostAndPort).build();
    }

    /* builder pattern */
    private ConsulRegistry(Builder builder) {
        this.hostname = builder.hostname;
        this.port = builder.port;
        HostAndPort hostAndPort = HostAndPort.fromParts(hostname, port);
        this.consul = Consul.builder().withHostAndPort(hostAndPort).build();
    }

    @Override
    public Object lookupByName(String key) {
        // Substitute $ character in key
        key = key.replaceAll("\\$", "/");
        kvClient = consul.keyValueClient();
        Optional<String> result = kvClient.getValueAsString(key);
        if (result.isPresent()) {
            byte[] postDecodedValue = Base64.decodeBase64(result.get());
            return SerializationUtils.deserialize(postDecodedValue);
        }
        return null;
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object object = lookupByName(name);
        if (object == null) {
            return null;
        }
        try {
            return type.cast(object);
        } catch (Throwable e) {
            String msg = "Found bean: " + name + " in Consul Registry: " + this + " of type: "
                    + object.getClass().getName() + "expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Object obj = null;
        Map<String, T> result = new HashMap<String, T>();
        // encode $ signs as they occur in subclass types
        String keyPrefix = type.getName().replaceAll("\\$", "/");
        kvClient = consul.keyValueClient();
        List<String> keys;
        try {
            keys = kvClient.getKeys(keyPrefix);
        } catch (ConsulException e) {
            return result;
        }
        if (keys != null) {
            for (String key : keys) {
                // change bookmark back into actual key
                key = key.substring(key.lastIndexOf('/') + 1);
                obj = lookupByName(key.replaceAll("\\$", "/"));
                if (type.isInstance(obj)) {
                    result.put(key, type.cast(obj));
                }
            }
        }
        return result;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        String keyPrefix = type.getName().replaceAll("\\$", "/");
        Object object = null;
        Set<T> result = new HashSet<T>();
        List<String> keys = null;
        try {
            keys = kvClient.getKeys(keyPrefix);
        } catch (ConsulException e) {
            return result;
        }
        if (keys != null) {
            for (String key : keys) {
                // change bookmark back into actual key
                key = key.substring(key.lastIndexOf('/') + 1);
                object = lookupByName(key.replaceAll("\\$", "/"));
                if (type.isInstance(object)) {
                    result.add(type.cast(object));
                }
            }
        }
        return result;
    }

    public void remove(String key) {
        // create session to avoid conflicts (not sure if that is safe enough)
        SessionClient sessionClient = consul.sessionClient();
        String sessionName = "session_" + UUID.randomUUID().toString();

        SessionCreatedResponse response = sessionClient
                .createSession(ImmutableSession.builder().name(sessionName).build());
        String sessionId = response.getId();
        kvClient = consul.keyValueClient();
        String lockKey = "lock_" + key;
        kvClient.acquireLock(lockKey, sessionName, sessionId);
        Object object = lookupByName(key);
        if (object == null) {
            String msg = "Bean with key '" + key + "' did not exist in Consul Registry.";
            throw new NoSuchBeanException(msg);
        }
        kvClient.deleteKey(key);
        kvClient.deleteKey(object.getClass().getName() + "/" + key);
        kvClient.releaseLock(lockKey, sessionId);
    }

    public void put(String key, Object object) {
        // Substitute $ character in key
        key = key.replaceAll("\\$", "/");
        // create session to avoid conflicts
        // (not sure if that is safe enough, again)
        SessionClient sessionClient = consul.sessionClient();
        String sessionName = "session_" + UUID.randomUUID().toString();
        SessionCreatedResponse response = sessionClient
                .createSession(ImmutableSession.builder().name(sessionName).build());
        String sessionId = response.getId();
        kvClient = consul.keyValueClient();
        String lockKey = "lock_" + key;
        kvClient.acquireLock(lockKey, sessionName, sessionId);

        // Allow only unique keys, last one wins
        if (lookupByName(key) != null) {
            remove(key);
        }
        Object clone = SerializationUtils.clone((Serializable) object);
        byte[] serializedObject = SerializationUtils.serialize((Serializable) clone);
        // pre-encode due native encoding issues
        byte[] preEncodedValue = Base64.encodeBase64(serializedObject);
        String value = new String(preEncodedValue);
        // store the actual class
        kvClient.putValue(key, value);
        // store just as a bookmark
        kvClient.putValue(object.getClass().getName().replaceAll("\\$", "/") + "/" + key, "1");
        kvClient.releaseLock(lockKey, sessionId);
    }

    public static class Builder {
        // required parameter
        String hostname;
        // optional parameter
        Integer port = 8500;

        public Builder(String hostname) {
            this.hostname = hostname;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public ConsulRegistry build() {
            return new ConsulRegistry(this);
        }
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public Object lookup(String name) {
        return lookupByName(name);
    }

    @Override
    public <T> T lookup(String name, Class<T> type) {
        return lookupByNameAndType(name, type);
    }

    @Override
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return lookupByType(type);
    }

}
