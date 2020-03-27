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

public class SpringBase64DataFormatLineLengthTest extends SpringBase64DataFormatTestBase {

    private static final String ENCODED = "IrRWhNZNjFxQ6WXJEIsehbnFdurtgacAq+t6Zh3uYlyclF3HAx995mbIydQlymM8\r\n"
            + "V3yA+Yb1p3Ij7AS1VQaUNHAljNpHUqrWR6EmASZV/EQvR5Gk8XDvRrrtkoDm+jdZ\r\n"
            + "/XKfest2OIzhixZF1mcqyi1PHep/rFnVPclO9WOWtCCRhz+U2soBzNBtvTc6x1pz\r\n"
            + "1gOZcoOEFKHSf2kmkq1/7hHFl5Cb9nbSBgyplFzsInVBfCkRxXAFixwbC3B+LB8e\r\n"
            + "15zSMvoG6okyDs7C8QShIZCXGHlsuUiH96izUbfB8qpTQK80PPAisxYhF/gb678w\r\n"
            + "vO5e/03AmFmYbBqzwoNQ6PoZKFI8a4PUrLoCLrUnKQgwOXueb1y8d4bsVGrXH5QU\r\n"
            + "FgAE3yZEn2ZQtVv6bZnm3lvBe/LLRD4xIU2Pcm5e+DJUZhHcl/8MaioDWFgYPLft\r\n"
            + "DKvEUwLB3IFWLSKMKFoeXn2nkwxsCHrzhajhbkKl1+H9I7Gkd19DyAoPIriWOJSc\r\n"
            + "og+mcP0iqG9iMqYFko2nrh2rr+jcyKFBhrRUuNw3W8+h+FOwZDLcBmuTv2lEOvUd\r\n"
            + "aPgD+1e6fXpuxhiih4wf/zlakeVa031T9c0/HN02z0cAhLT1vtEA0zDn6OzzhY//\r\n"
            + "Mh332ZmC+xro+e9o2a6+dnwamDtLuRgDDd+EcoUQpfELXobX3ZSX7OQw1ZXxWiJL\r\n"
            + "tSOc5yLRkdbxdLK/C6fkcY4cqc/RwBGYtXN7Z1ENG/s/LnrZnRU/ErMWRtbRwehA\r\n"
            + "/0a2KSbNOMwK8BpzDruXufLXZcGaDKRUektQfdX4XhhYESt1drewlQLVaEWrZBR8\r\n"
            + "JOd5mckulPhwHp2Q00YyoScEj6Rs/9siyv49/FSaRCbnfzl3CRnNvCOD1cvF4One\r\n"
            + "YbVJCMOY49ucFmN/mBCyxLOtJ4Zz8EG1FC81QTg3Scw+FdFDsCgr7DqVrmPOLikq\r\n"
            + "q6wJdLBjyHXuMiVP9Fq/aAxvXEgjRuVnN20wn2tUOXeaN4XqziQ66M229HsY0BX5\r\n"
            + "riJ00yXArDxd+I9mFDpw/UDnGBAE2P//1fU1ns1A6zQ6hTv7axdlw3/FnOAdymEK\r\n"
            + "qED9CPfbiDvJygcAcxv2fyORHQ+TiprMGxckAlnLZ2pGl+gOzbtZzJgecyFJHBbh\r\n"
            + "tkubGD4zzQhuJJw8ypqppSxqDs8SAW2frj42UT9qRMeCBGXLa1wyISt4GI6iOnfw\r\n"
            + "TCRJ/SE7CVrEfmdmROlJpAJHfUlQIJq1aW3mTE5zTmAygypxRUDCmA+eY9wdCicF\r\n"
            + "p6YptdCEK3P27QzZsSASAByd5jxHMiIBkdwGzj1501xZ7hFLJDXDTQ==\r\n";

    @Test
    void testEncode() throws Exception {
        runEncoderTest(DECODED, ENCODED.getBytes());
    }

    @Test
    void testDecode() throws Exception {
        runDecoderTest(ENCODED.getBytes(), DECODED);
    }
    
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/dataformat/base64/SpringBase64DataFormatLineLengthTest.xml");
    }

}
