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
package org.apache.camel.dataformat.base64;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.dataformat.base64.Base64TestConstants.DECODED;

public class SpringBase64DataFormatLineEndingsTest extends SpringBase64DataFormatTestBase {

    private static final String ENCODED = "IrRWhNZNjFxQ6WXJEIsehbnFdurtgacAq+t6Zh3uYlyclF3HAx995mbIydQlymM8V3yA+Yb1p3Ij\n"
            + "7AS1VQaUNHAljNpHUqrWR6EmASZV/EQvR5Gk8XDvRrrtkoDm+jdZ/XKfest2OIzhixZF1mcqyi1P\n"
            + "Hep/rFnVPclO9WOWtCCRhz+U2soBzNBtvTc6x1pz1gOZcoOEFKHSf2kmkq1/7hHFl5Cb9nbSBgyp\n"
            + "lFzsInVBfCkRxXAFixwbC3B+LB8e15zSMvoG6okyDs7C8QShIZCXGHlsuUiH96izUbfB8qpTQK80\n"
            + "PPAisxYhF/gb678wvO5e/03AmFmYbBqzwoNQ6PoZKFI8a4PUrLoCLrUnKQgwOXueb1y8d4bsVGrX\n"
            + "H5QUFgAE3yZEn2ZQtVv6bZnm3lvBe/LLRD4xIU2Pcm5e+DJUZhHcl/8MaioDWFgYPLftDKvEUwLB\n"
            + "3IFWLSKMKFoeXn2nkwxsCHrzhajhbkKl1+H9I7Gkd19DyAoPIriWOJScog+mcP0iqG9iMqYFko2n\n"
            + "rh2rr+jcyKFBhrRUuNw3W8+h+FOwZDLcBmuTv2lEOvUdaPgD+1e6fXpuxhiih4wf/zlakeVa031T\n"
            + "9c0/HN02z0cAhLT1vtEA0zDn6OzzhY//Mh332ZmC+xro+e9o2a6+dnwamDtLuRgDDd+EcoUQpfEL\n"
            + "XobX3ZSX7OQw1ZXxWiJLtSOc5yLRkdbxdLK/C6fkcY4cqc/RwBGYtXN7Z1ENG/s/LnrZnRU/ErMW\n"
            + "RtbRwehA/0a2KSbNOMwK8BpzDruXufLXZcGaDKRUektQfdX4XhhYESt1drewlQLVaEWrZBR8JOd5\n"
            + "mckulPhwHp2Q00YyoScEj6Rs/9siyv49/FSaRCbnfzl3CRnNvCOD1cvF4OneYbVJCMOY49ucFmN/\n"
            + "mBCyxLOtJ4Zz8EG1FC81QTg3Scw+FdFDsCgr7DqVrmPOLikqq6wJdLBjyHXuMiVP9Fq/aAxvXEgj\n"
            + "RuVnN20wn2tUOXeaN4XqziQ66M229HsY0BX5riJ00yXArDxd+I9mFDpw/UDnGBAE2P//1fU1ns1A\n"
            + "6zQ6hTv7axdlw3/FnOAdymEKqED9CPfbiDvJygcAcxv2fyORHQ+TiprMGxckAlnLZ2pGl+gOzbtZ\n"
            + "zJgecyFJHBbhtkubGD4zzQhuJJw8ypqppSxqDs8SAW2frj42UT9qRMeCBGXLa1wyISt4GI6iOnfw\n"
            + "TCRJ/SE7CVrEfmdmROlJpAJHfUlQIJq1aW3mTE5zTmAygypxRUDCmA+eY9wdCicFp6YptdCEK3P2\n"
            + "7QzZsSASAByd5jxHMiIBkdwGzj1501xZ7hFLJDXDTQ==\n";

    
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/dataformat/base64/SpringBase64DataFormatLineEndingsTest.xml");
    }

    @Test
    void testEncode() throws Exception {
        runEncoderTest(DECODED, ENCODED.getBytes());
    }

    @Test
    void testDecode() throws Exception {
        runDecoderTest(ENCODED.getBytes(), DECODED);
    }
    
    
}
