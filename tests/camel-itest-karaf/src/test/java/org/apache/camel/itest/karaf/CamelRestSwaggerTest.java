package org.apache.camel.itest.karaf;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class CamelRestSwaggerTest extends BaseKarafTest {

    public static final String COMPONENT = extractName(CamelRestSwaggerTest.class);

    @Test
    public void test() throws Exception {
        testComponent(COMPONENT);
    }
}
