package org.apache.camel.dataformat.protobuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Message;
import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProtobufConverterTest {

    @Test
    public void testt() {
        final Map<String, Object> input = new HashMap<>();
        final Map<String, Object> phoneNumber = new HashMap<>();
        phoneNumber.put("number", "011122233");
        phoneNumber.put("type", 0);

        final Map<String, Object> phoneNumber2 = new HashMap<>();
        phoneNumber2.put("number", "5542454");
        phoneNumber2.put("type", 2);

        input.put("name", "Martin");
        input.put("id", 1234);
        input.put("phone", Arrays.asList(phoneNumber, phoneNumber2));
        input.put("email", "dd");
        input.put("nicknames", Arrays.asList("awesome1", "awesome2"));

        final Map<String, Object> address = new HashMap<>();
        address.put("street", "awesome street");
        address.put("street_number", 12);
        address.put("is_valid", false);

        input.put("address", address);

        final ProtobufConverter protobufConverter = ProtobufConverter.create(AddressBookProtos.Person.getDefaultInstance());
        final AddressBookProtos.Person message = (AddressBookProtos.Person) protobufConverter.toProto(input);

        System.out.println(message);

    }

}