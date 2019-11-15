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
package org.apache.camel.component.cxf.wssecurity.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

public class UTPasswordCallback implements CallbackHandler {

    private Map<String, String> passwords =
        new HashMap<>();

    public UTPasswordCallback() {
        passwords.put("Alice", "ecilA");
        passwords.put("abcd", "dcba");
        passwords.put("alice", "password");
        passwords.put("bob", "password");
    }

    /**
     * Here, we attempt to get the password from the private
     * alias/passwords map.
     */
    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            try {
                String id = (String)callback.getClass().getMethod("getIdentifier").invoke(callback);

                String pass = passwords.get(id);
                if (pass != null) {
                    callback.getClass().getMethod("setPassword", String.class).invoke(callback, pass);
                    return;
                }
            } catch (Exception ex) {
                UnsupportedCallbackException e = new UnsupportedCallbackException(callback);
                e.initCause(ex);
                throw e;
            }
        }
    }

    /**
     * Add an alias/password pair to the callback mechanism.
     */
    public void setAliasPassword(String alias, String password) {
        passwords.put(alias, password);
    }
}
