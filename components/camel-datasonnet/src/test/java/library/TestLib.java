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
package library;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.datasonnet.header.Header;
import com.datasonnet.jsonnet.Val;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import org.springframework.stereotype.Component;

@Component
public class TestLib extends Library {

    private static final TestLib INSTANCE = new TestLib();

    public TestLib() {
    }

    public static TestLib getInstance() {
        return INSTANCE;
    }

    @Override
    public String namespace() {
        return "testlib";
    }

    @Override
    public Map<String, Val.Func> functions(DataFormatService dataFormats, Header header) {
        Map<String, Val.Func> answer = new HashMap<>();
        answer.put("sayHello", makeSimpleFunc(
                Collections.emptyList(), //parameters list
                new Function<List<Val>, Val>() {
                    @Override
                    public Val apply(List<Val> vals) {
                        return new Val.Str("Hello, World");
                    }
                }));
        return answer;
    }

    @Override
    public Map<String, Val.Obj> modules(DataFormatService dataFormats, Header header) {
        return Collections.emptyMap();
    }

    @Override
    public Set<String> libsonnets() {
        return Collections.emptySet();
    }
}
