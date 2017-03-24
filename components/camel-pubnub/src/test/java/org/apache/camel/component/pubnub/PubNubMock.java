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

package org.apache.camel.component.pubnub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubException;

import org.json.JSONArray;
import org.json.JSONObject;

public class PubNubMock extends Pubnub {
    private static Map<String, Callback> subscribers = new ConcurrentHashMap<String, Callback>();
    private static Map<String, Callback> presenceSubscribers = new ConcurrentHashMap<String, Callback>();
    private static Map<String, JSONObject> stateMap = new ConcurrentHashMap<String, JSONObject>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public PubNubMock(String publishKey, String subscribeKey) {
        super(publishKey, subscribeKey);
    }

    @Override
    public void subscribe(String channel, Callback callback) throws PubnubException {
        subscribers.put(channel, callback);
        executorService.execute(() -> {
            try {
                Thread.sleep(500);
                callback.connectCallback(channel, "OK");
            } catch (InterruptedException e) {
            }
        });
        Callback presenceCallback = presenceSubscribers.get(channel);
        if (presenceCallback != null) {
            executorService.execute(() -> {
                try {
                    Thread.sleep(500);
                    String presence = "{\"action\":\"join\",\"timestamp\":1431777382,\"uuid\":\"d08f121b-d146-45af-a814-058c1b7d283a\",\"occupancy\":1}";
                    presenceCallback.successCallback(channel, new JSONObject(presence), "" + System.currentTimeMillis());
                } catch (Exception e) {
                }
            });
        }
    }

    @Override
    public void publish(String channel, JSONObject message, Callback callback) {
        callback.successCallback(channel, "OK");
        Callback clientMockCallback = subscribers.get(channel);
        if (clientMockCallback != null) {
            executorService.execute(() -> {
                try {
                    Thread.sleep(1000);
                    clientMockCallback.successCallback(channel, message, "" + System.currentTimeMillis());
                } catch (InterruptedException e) {
                }
            });
        }
    }

    @Override
    public void publish(String channel, JSONArray message, Callback callback) {
        callback.successCallback(channel, "OK");
        Callback clientMockCallback = subscribers.get(channel);
        if (clientMockCallback != null) {
            executorService.execute(() -> {
                try {
                    Thread.sleep(1000);
                    clientMockCallback.successCallback(channel, message, "" + System.currentTimeMillis());
                } catch (InterruptedException e) {
                }
            });
        }
    }

    @Override
    public void publish(String channel, String message, Callback callback) {
        callback.successCallback(channel, "OK");
        Callback clientMockCallback = subscribers.get(channel);
        if (clientMockCallback != null) {
            executorService.execute(() -> {
                try {
                    Thread.sleep(1000);
                    clientMockCallback.successCallback(channel, message, "" + System.currentTimeMillis());
                } catch (InterruptedException e) {
                }
            });
        }
    }

    @Override
    public void presence(String channel, Callback callback) throws PubnubException {
        presenceSubscribers.put(channel, callback);
        executorService.execute(() -> {
            try {
                Thread.sleep(1000);
                callback.connectCallback(channel, "OK");
            } catch (InterruptedException e) {
            }
        });
    }

    @Override
    public void history(String channel, boolean reverse, Callback callback) {
        executorService.execute(() -> {
            try {
                Thread.sleep(1000);
                callback.successCallback(channel, new JSONArray("[[\"message1\", \"message2\", \"message3\"],\"Start Time Token\",\"End Time Token\"]"));
            } catch (Exception e) {
            }
        });
    }

    @Override
    public void setState(String channel, String uuid, JSONObject state, Callback callback) {
        stateMap.put(uuid, state);
        executorService.execute(() -> {
            try {
                Thread.sleep(1000);
                callback.successCallback(channel, "OK");
            } catch (Exception e) {
            }
        });
    }

    @Override
    public void getState(String channel, String uuid, Callback callback) {
        JSONObject jsonObject = stateMap.get(uuid);
        executorService.execute(() -> {
            try {
                Thread.sleep(1000);
                callback.successCallback(channel, jsonObject);
            } catch (Exception e) {
            }
        });
    }

    @Override
    public void hereNow(String channel, boolean state, boolean uuids, Callback callback) {

        executorService.execute(() -> {
            try {
                Thread.sleep(500);
                //@formatter:off
                JSONObject response = new JSONObject("{\"uuids\":[\"76c2c571-9a2b-d074-b4f8-e93e09f49bd\"," 
                                                    + "\"175c2c67-b2a9-470d-8f4b-1db94f90e39e\", "
                                                    + "\"2c67175c-2a9b-074d-4b8f-90e39e1db94f\"]," 
                                                    + "\"occupancy\":3 }");
                //@formatter:on
                callback.successCallback(channel, response);
            } catch (Exception e) {
            }

        });
    }

    @Override
    public void whereNow(String uuid, Callback callback) {
        executorService.execute(() -> {
            try {
                Thread.sleep(1000);
                callback.successCallback("channel", new JSONObject("{\"channels\":[\"hello_world\"]}"));
            } catch (Exception e) {
            }
        });
    }
}
