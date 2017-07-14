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
package sample.camel;

import java.util.Collections;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Used for simulating a rest service which we can run locally inside Spring Boot
 */
@RestController
public class PetController {

    private static final String[] PETS = new String[]{"Snoopy", "Fido", "Tony the Tiger"};

    @GetMapping(value = "/pets/{id}")
    public Map<String, String> petById(@PathVariable("id") Integer id) {
        if (id != null && id > 0 && id <= PETS.length + 1) {
            int index = id - 1;
            String pet = PETS[index];
            return Collections.singletonMap("name", pet);
        } else {
            return Collections.emptyMap();
        }
    }

}
