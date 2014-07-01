package org.apache.camel.metrics;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.EnumSet;

import org.junit.Test;

public class MetricsTypeTest {

    @Test
    public void testGetByName() throws Exception {
        for (MetricsType type : EnumSet.allOf(MetricsType.class)) {
            MetricsType t = MetricsType.getByName(type.toString());
            assertThat(t, is(type));
        }
    }
}
