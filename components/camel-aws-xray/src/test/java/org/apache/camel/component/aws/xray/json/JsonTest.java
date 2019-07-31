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
package org.apache.camel.component.aws.xray.json;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class JsonTest {

    @Test
    public void testJsonParse() {
        JsonStructure json = JsonParser.parse("{\n"
                + "  \"test\": \"some string\",\n"
                + "  \"otherKey\": true,\n"
                + "  \"nextKey\": 1234,\n"
                + "  \"doubleKey\": 1234.567,\n"
                + "  \"subElement\": {\n"
                + "    \"subKey\": \"some other string\",\n"
                + "    \"complexString\": \"String with JSON syntax elements like .,\\\" { or }\"\n"
                + "  },\n"
                + "  \"arrayElement\": [\n"
                + "    {\n"
                + "      \"id\": 1,\n"
                + "      \"name\": \"test1\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"id\": 2,\n"
                + "      \"name\": \"test2\"\n"
                + "    }\n"
                + "  ]\n"
                + "}");

        assertThat(json, is(notNullValue()));
        assertThat(json, is(instanceOf(JsonObject.class)));
        JsonObject jsonObj = (JsonObject) json;
        assertThat(jsonObj.getKeys().size(), is(equalTo(6)));
        assertThat(jsonObj.getString("test"), is(equalTo("some string")));
        assertThat(jsonObj.getBoolean("otherKey"), is(equalTo(true)));
        assertThat(jsonObj.getInteger("nextKey"), is(equalTo(1234)));
        assertThat(jsonObj.getDouble("doubleKey"), is(equalTo(1234.567)));
        assertThat(jsonObj.get("subElement"), is(instanceOf(JsonObject.class)));
        JsonObject jsonSub = (JsonObject) jsonObj.get("subElement");
        assertThat(jsonSub.getString("subKey"), is(equalTo("some other string")));
        assertThat(jsonSub.getString("complexString"), is(equalTo("String with JSON syntax elements like .,\\\" { or }")));
        assertThat(jsonObj.get("arrayElement"), is(instanceOf(JsonArray.class)));
        JsonArray jsonArr = (JsonArray) jsonObj.get("arrayElement");
        assertThat(jsonArr.size(), is(equalTo(2)));
        assertThat(jsonArr.get(0), is(instanceOf(JsonObject.class)));
        JsonObject arrElem0 = (JsonObject) jsonArr.get(0);
        assertThat(arrElem0.getInteger("id"), is(equalTo(1)));
        assertThat(arrElem0.getString("name"), is(equalTo("test1")));
        assertThat(jsonArr.get(1), is(instanceOf(JsonObject.class)));
        JsonObject arrElem1 = (JsonObject) jsonArr.get(1);
        assertThat(arrElem1.getInteger("id"), is(equalTo(2)));
        assertThat(arrElem1.getString("name"), is(equalTo("test2")));
    }

    @Test
    public void testJsonParseSample() {

        JsonStructure json = JsonParser.parse("{"
                + "  \"name\":\"b\","
                + "  \"id\":\"6ae1778525198ce8\","
                + "  \"start_time\":1.50947752281E9,"
                + "  \"trace_id\":\"1-59f8cc92-4819a77b4109de34405a5643\","
                + "  \"end_time\":1.50947752442E9,"
                + "  \"aws\":{"
                + "    \"xray\":{"
                + "      \"sdk_version\":\"1.2.0\","
                + "      \"sdk\":\"X-Ray for Java\""
                + "    }"
                + "  },"
                + "  \"service\":{"
                + "    \"runtime\":\"Java HotSpot(TM) 64-Bit Server VM\","
                + "    \"runtime_version\":\"1.8.0_144\""
                + "  }"
                + "}"
                + "}");

        assertThat(json, is(notNullValue()));
        JsonObject jsonObj = (JsonObject) json;
        assertThat(jsonObj.getKeys().size(), is(equalTo(7)));
        assertThat(jsonObj.getString("name"), is(equalTo("b")));
        assertThat(jsonObj.getString("id"), is(equalTo("6ae1778525198ce8")));
        assertThat(jsonObj.getString("trace_id"), is(equalTo("1-59f8cc92-4819a77b4109de34405a5643")));
        assertThat(jsonObj.getDouble("start_time"), is(equalTo(1.50947752281E9)));
        assertThat(jsonObj.getDouble("end_time"), is(equalTo(1.50947752442E9)));
        assertThat(jsonObj.get("aws"), is(instanceOf(JsonObject.class)));
        JsonObject aws = (JsonObject) jsonObj.get("aws");
        assertThat(aws.get("xray"), is(instanceOf(JsonObject.class)));
        JsonObject xray = (JsonObject) aws.get("xray");
        assertThat(xray.getString("sdk_version"), is(equalTo("1.2.0")));
        assertThat(xray.getString("sdk"), is(equalTo("X-Ray for Java")));
        assertThat(jsonObj.get("service"), is(instanceOf(JsonObject.class)));
        JsonObject service = (JsonObject) jsonObj.get("service");
        assertThat(service.getString("runtime"), is(equalTo("Java HotSpot(TM) 64-Bit Server VM")));
        assertThat(service.getString("runtime_version"), is(equalTo("1.8.0_144")));
    }

    @Test
    public void testJsonParseWithArray() {
        JsonStructure json = JsonParser.parse("{"
                + "  \"name\":\"c\","
                + "  \"id\":\"6ada7c7013b2c681\","
                + "  \"start_time\":1.509484895232E9,"
                + "  \"trace_id\":\"1-59f8e935-11c64d09c90803f69534c9af\","
                + "  \"end_time\":1.509484901458E9,"
                + "  \"subsegments\":["
                + "    {"
                + "      \"name\":\"SendingTo_log_test\","
                + "      \"id\":\"545118f5c69e2973\","
                + "      \"start_time\":1.509484895813E9,"
                + "      \"end_time\":1.509484896709E9"
                + "    }"
                + "  ],"
                + "  \"aws\":{"
                + "    \"xray\":{"
                + "      \"sdk_version\":\"1.2.0\","
                + "      \"sdk\":\"X-Ray for Java\""
                + "    }"
                + "  },"
                + "  \"service\":{"
                + "    \"runtime\":\"Java HotSpot(TM) 64-Bit Server VM\","
                + "    \"runtime_version\":\"1.8.0_144\""
                + "  }"
                + "}\u0000\u0000");

        assertThat(json, is(notNullValue()));
        JsonObject jsonObj = (JsonObject) json;
        assertThat(jsonObj.getKeys().size(), is(equalTo(8)));
        assertThat(jsonObj.getString("name"), is(equalTo("c")));
        assertThat(jsonObj.getString("id"), is(equalTo("6ada7c7013b2c681")));
        assertThat(jsonObj.getString("trace_id"), is(equalTo("1-59f8e935-11c64d09c90803f69534c9af")));
        assertThat(jsonObj.getDouble("start_time"), is(equalTo(1.509484895232E9)));
        assertThat(jsonObj.getDouble("end_time"), is(equalTo(1.509484901458E9)));
        assertThat(jsonObj.get("aws"), is(instanceOf(JsonObject.class)));
        JsonObject aws = (JsonObject) jsonObj.get("aws");
        assertThat(aws.get("xray"), is(instanceOf(JsonObject.class)));
        JsonObject xray = (JsonObject) aws.get("xray");
        assertThat(xray.getString("sdk_version"), is(equalTo("1.2.0")));
        assertThat(xray.getString("sdk"), is(equalTo("X-Ray for Java")));
        assertThat(jsonObj.get("service"), is(instanceOf(JsonObject.class)));
        JsonObject service = (JsonObject) jsonObj.get("service");
        assertThat(service.getString("runtime"), is(equalTo("Java HotSpot(TM) 64-Bit Server VM")));
        assertThat(service.getString("runtime_version"), is(equalTo("1.8.0_144")));
        assertThat(jsonObj.get("subsegments"), is(instanceOf(JsonArray.class)));
        JsonArray array = (JsonArray) jsonObj.get("subsegments");
        assertThat(array.size(), is(equalTo(1)));
        assertThat(array.get(0), is(instanceOf(JsonObject.class)));
        JsonObject arrItem = (JsonObject) array.get(0);
        assertThat(arrItem.getKeys().size(), is(equalTo(4)));
        assertThat(arrItem.getString("name"), is(equalTo("SendingTo_log_test")));
        assertThat(arrItem.getString("id"), is(equalTo("545118f5c69e2973")));
        assertThat(arrItem.getDouble("start_time"), is(equalTo(1.509484895813E9)));
        assertThat(arrItem.getDouble("end_time"), is(equalTo(1.509484896709E9)));
    }
}
