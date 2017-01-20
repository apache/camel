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
package org.apache.camel.component.ironmq;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import io.iron.ironmq.Client;
import io.iron.ironmq.EmptyQueueException;
import io.iron.ironmq.HTTPException;
import io.iron.ironmq.Ids;
import io.iron.ironmq.Message;
import io.iron.ironmq.MessageOptions;
import io.iron.ironmq.Messages;
import io.iron.ironmq.Queue;

public class MockQueue extends Queue {
    private Map<String, Message> messages = new LinkedHashMap<String, Message>();

    public MockQueue(Client client, String name) {
        super(client, name);
    }

    @Override
    public String push(String msg, long delay) throws IOException {
        String randint = new BigInteger(24 * 8, new Random()).toString(16);
        Message message = new Message();
        message.setBody(msg);
        message.setDelay(delay);
        message.setId(randint);
        message.setReservationId(UUID.randomUUID().toString());
        messages.put(randint, message);
        return randint;
    }

    @Override
    public Ids pushMessages(String[] msg, long delay) throws IOException {
        for (String messageName : msg) {
            Message message = new Message();
            message.setBody(messageName);
            message.setDelay(delay);
            String randint = new BigInteger(24 * 8, new Random()).toString(16);
            message.setId(randint);
            message.setReservationId(UUID.randomUUID().toString());
            messages.put(randint, message);
        }
        Ids ids = null;
        try {
            Constructor<Ids> constructor = Ids.class.getDeclaredConstructor(Messages.class);
            constructor.setAccessible(true);
            Messages messageList = new Messages(new ArrayList<Message>(messages.values()));
            ids = constructor.newInstance(messageList);
        } catch (Exception e) {
        }
        return ids;
    }

    @Override
    public void deleteMessage(String id, String reservationId) throws IOException {
        if (messages.containsKey(id)) {
            messages.remove(id);
        } else {
            throw new HTTPException(404, "not found");
        }
    }

    @Override
    public void deleteMessages(Messages messages) throws IOException {
        MessageOptions[] messageOptions = messages.toMessageOptions();
        for (MessageOptions messageOption : messageOptions) {
            deleteMessage(messageOption.getId(), messageOption.getReservationId());
        }
    }

    @Override
    public Message peek() throws IOException {
        if (messages.size() > 0) {
            return messages.entrySet().iterator().next().getValue();
        }
        throw new EmptyQueueException();
    }

    @Override
    public Message reserve() throws IOException {
        if (messages.size() > 0) {
            Entry<String, Message> next = messages.entrySet().iterator().next();
            return next.getValue();
        }
        throw new EmptyQueueException();
    }

    @Override
    public Messages reserve(int numberOfMessages) throws IOException {
        return reserve(numberOfMessages, 120);
    }

    @Override
    public Messages reserve(int numberOfMessages, int timeout, int wait) throws IOException {
        if (messages.size() > 0) {

            Iterator<Entry<String, Message>> iterator = messages.entrySet().iterator();
            int i = 0;
            List<Message> list = new ArrayList<Message>();
            while (iterator.hasNext() && i < numberOfMessages) {
                Entry<String, Message> next = iterator.next();
                list.add(next.getValue());
                i++;
            }
            Messages messages = new Messages(list.toArray(new Message[list.size()]));
            return messages;
        }
        throw new EmptyQueueException();
    }

    void add(Message message) {
        messages.put(message.getId(), message);
    }
}
