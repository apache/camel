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
package org.apache.camel.component.bean;

/**
 * An example POJO with no annotations or interfaces to test out the POJO
 * Camel binding
 *
 * @version 
 */
public class ExampleBean {

    public String sayHello(String name) {
        return "Hello " + name + "!";
    }

    public String sayGoodbye(String name) {
        return "Bye " + name + "!";       
    }

    public long timesTwo(int value) {
        return value * 2;
    }
}