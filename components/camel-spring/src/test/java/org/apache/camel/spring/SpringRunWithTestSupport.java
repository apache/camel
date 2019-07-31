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
package org.apache.camel.spring;

import java.lang.annotation.Inherited;

import org.apache.camel.TestSupport;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * As the {@link RunWith} annotation is flagged to be {@link Inherited} we
 * make use of this class as the base class of those tests where we need
 * {@link SpringJUnit4ClassRunner} as the test runner but at the same time
 * require the useful testing methods provided by {@link TestSupport}.
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringRunWithTestSupport extends TestSupport {

}
