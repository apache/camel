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
package org.apache.camel.dataformat.protobuf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProtobufConverterTest {

    @Test
    public void testIfCorrectlyParseMap() {
        final Map<String, Object> phoneNumber = new HashMap<>();
        phoneNumber.put("number", "011122233");
        phoneNumber.put("type", "MOBILE");

        final Map<String, Object> phoneNumber2 = new HashMap<>();
        phoneNumber2.put("number", "5542454");
        phoneNumber2.put("type", 2);

        final Map<String, Object> address = new HashMap<>();
        address.put("street", "awesome street");
        address.put("street_number", 12);
        address.put("is_valid", false);

        final Map<String, Object> input = new HashMap<>();

        input.put("name", "Martin");
        input.put("id", 1234);
        input.put("phone", Arrays.asList(phoneNumber, phoneNumber2));
        input.put("email", "some@some.com");
        input.put("nicknames", Arrays.asList("awesome1", "awesome2"));
        input.put("address", address);

        final AddressBookProtos.Person message = (AddressBookProtos.Person) ProtobufConverter.toProto(input, AddressBookProtos.Person.getDefaultInstance());

        // assert primitives types and strings
        assertEquals("Martin", message.getName());
        assertEquals(1234, message.getId());
        assertEquals("some@some.com", message.getEmail());

        // assert nested message
        assertEquals("awesome street", message.getAddress().getStreet());
        assertEquals(12, message.getAddress().getStreetNumber());
        assertFalse(message.getAddress().getIsValid());

        // assert repeated messages
        assertEquals("011122233", message.getPhone(0).getNumber());
        assertEquals("MOBILE", message.getPhone(0).getType().name());
        assertEquals("5542454", message.getPhone(1).getNumber());
        assertEquals("WORK", message.getPhone(1).getType().name());

        assertEquals("awesome1", message.getNicknames(0));
        assertEquals("awesome2", message.getNicknames(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfThrowsErrorInCaseNestedMessageNotMap() {
        final Map<String, Object> input = new HashMap<>();

        input.put("name", "Martin");
        input.put("id", 1234);
        input.put("address", "wrong address");

        final AddressBookProtos.Person message = (AddressBookProtos.Person) ProtobufConverter.toProto(input, AddressBookProtos.Person.getDefaultInstance());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIfThrowsErrorInCaseRepeatedFieldIsNotList() {
        final Map<String, Object> input = new HashMap<>();

        input.put("name", "Martin");
        input.put("id", 1234);
        input.put("nicknames", "wrong nickname");

        final AddressBookProtos.Person message = (AddressBookProtos.Person) ProtobufConverter.toProto(input, AddressBookProtos.Person.getDefaultInstance());
    }

    @Test
    public void testIfItCorrectlyConvertMessageToMap() {
        final Map<String, Object> phoneNumber = new HashMap<>();
        phoneNumber.put("number", "011122233");
        phoneNumber.put("type", "MOBILE");

        final Map<String, Object> phoneNumber2 = new HashMap<>();
        phoneNumber2.put("number", "5542454");
        phoneNumber2.put("type", "WORK");

        final Map<String, Object> address = new HashMap<>();
        address.put("street", "awesome street");
        address.put("street_number", 12);
        address.put("is_valid", false);

        final Map<String, Object> input = new HashMap<>();

        input.put("name", "Martin");
        input.put("id", 1234);
        input.put("phone", Arrays.asList(phoneNumber, phoneNumber2));
        input.put("email", "some@some.com");
        input.put("nicknames", Arrays.asList("awesome1", "awesome2"));
        input.put("address", address);

        final AddressBookProtos.Person message = (AddressBookProtos.Person) ProtobufConverter.toProto(input, AddressBookProtos.Person.getDefaultInstance());

        final Map<String, Object> resultedMessageMap = ProtobufConverter.toMap(message);

        assertEquals(input, resultedMessageMap);

    }

}
