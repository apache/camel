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
package org.apache.camel.processor.resequencer;

public class IntegerComparator implements SequenceElementComparator<Integer> {

    @Override
    public boolean predecessor(Integer o1, Integer o2) {
        return o1.intValue() == (o2.intValue() - 1);
    }

    @Override
    public boolean successor(Integer o1, Integer o2) {
        return o2.intValue() == (o1.intValue() - 1);
    }

    @Override
    public int compare(Integer o1, Integer o2) {
        return o1.compareTo(o2);
    }

    @Override
    public boolean isValid(Integer o1) {
        return o1 != null;
    }

}
