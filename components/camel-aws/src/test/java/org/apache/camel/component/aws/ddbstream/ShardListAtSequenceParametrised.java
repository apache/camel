package org.apache.camel.component.aws.ddbstream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ShardListAtSequenceParametrised {


    @Parameterized.Parameters
    public static Collection<Object[]> paramaters() {
        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{"0", "a"});
        results.add(new Object[]{"3", "a"});
        results.add(new Object[]{"6", "b"});
        results.add(new Object[]{"8", "b"});
        results.add(new Object[]{"15", "b"});
        results.add(new Object[]{"16", "c"});
        results.add(new Object[]{"18", "d"});
        results.add(new Object[]{"25", "d"});
        results.add(new Object[]{"30", "d"});
        return results;
    }

    private ShardList undertest;

    private final String inputSequenceNumber;
    private final String expectedShardId;

    public ShardListAtSequenceParametrised(String inputSequenceNumber, String expectedShardId) {
        this.inputSequenceNumber = inputSequenceNumber;
        this.expectedShardId = expectedShardId;
    }

    @Before
    public void setup() throws Exception {
        undertest = new ShardList();
        undertest.addAll(ShardListTest.createShardsWithSequenceNumbers(null,
                "a", "1", "5",
                "b", "8", "15",
                "c", "16", "16",
                "d", "20", null
        ));
    }

    @Test
    public void assertions() throws Exception {
        assertThat(undertest.atSeq(inputSequenceNumber).getShardId(), is(expectedShardId));
    }
}
