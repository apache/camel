package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.services.dynamodbv2.model.Shard;
import java.util.ArrayList;
import java.util.List;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;

public class ShardListTest {

    @Test
    public void nextReturnsShardWithParent() throws Exception {
        Shard first = new Shard()
                .withShardId("first_shard")
                .withParentShardId("other_shard_id");
        Shard second = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");

        ShardList shards = new ShardList();
        shards.add(first);
        shards.add(second);

        assertThat(shards.nextAfter(first), is(second));
    }

    @Test
    public void nextWithNullReturnsFirstKnownShard() throws Exception {
        Shard first = new Shard()
                .withShardId("first_shard");
        Shard second = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");

        ShardList shards = new ShardList();
        shards.add(first);
        shards.add(second);

        assertThat(shards.nextAfter(first), is(second));
    }

    @Test
    public void reAddingEntriesMaintainsOrder() throws Exception {
        Shard first = new Shard()
                .withShardId("first_shard");
        Shard second = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");

        ShardList shards = new ShardList();
        shards.add(first);
        shards.add(second);

        assertThat(shards.nextAfter(first), is(second));

        Shard second2 = new Shard()
                .withParentShardId("first_shard")
                .withShardId("second_shard");
        Shard third = new Shard()
                .withParentShardId("second_shard")
                .withShardId("third_shard");
        shards.add(second2);
        shards.add(third);

        assertThat(shards.nextAfter(first), is(second));
        assertThat(shards.nextAfter(second), is(third));
    }

    @Test
    public void firstShardGetsTheFirstWithoutAParent() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards(null, "a", "b", "c", "d"));

        assertThat(shards.first().getShardId(), is("a"));
    }

    @Test
    public void firstShardGetsTheFirstWithAnUnknownParent() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards("a", "b", "c", "d"));

        assertThat(shards.first().getShardId(), is("b"));
    }

    @Test
    public void removingShards() throws Exception {
        ShardList shards = new ShardList();
        shards.addAll(createShards(null, "a", "b", "c", "d"));
        Shard removeBefore = new Shard().withShardId("c").withParentShardId("b");
        shards.removeOlderThan(removeBefore);
        assertThat(shards.first().getShardId(), is("c"));
    }

    List<Shard> createShards(String initialParent, String... shardIds) {
        String previous = initialParent;
        List<Shard> result = new ArrayList<>();
        for (String s : shardIds) {
            result.add(new Shard().withShardId(s).withParentShardId(previous));
            previous = s;
        }
        return result;
    }
}