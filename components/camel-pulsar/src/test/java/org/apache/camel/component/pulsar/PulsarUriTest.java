package org.apache.camel.component.pulsar;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class PulsarUriTest {

    @Test(expected = IllegalArgumentException.class)
    public void incorrectComponentType() {
        new PulsarUri("error://persistent/tenant/namespace/topic");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingTopic() {
        new PulsarUri("pulsar://persistent/tenant/namespace/");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingNamespace() {
        new PulsarUri("pulsar://persistent/tenant//topic");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingTenant() {
        new PulsarUri("pulsar://persistent//namespace/topic");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingType() {
        new PulsarUri("pulsar:///tenant/namespace/topic");
    }

    @Test(expected = IllegalArgumentException.class)
    public void incorrectTopicType() {
        new PulsarUri("pulsar://notAType/tenant/namespace/topic");
    }

    @Test
    public void persistentTopicType() {
        PulsarUri uri = new PulsarUri("pulsar://persistent/tenant/namespace/topic");
        assertThat(uri.getType(), is("persistent"));
    }

    @Test
    public void nonPersistentTopicType() {
        PulsarUri uri = new PulsarUri("pulsar://non-persistent/tenant/namespace/topic");
        assertThat(uri.getType(), is("non-persistent"));
    }

    @Test
    public void fullUri() {
        PulsarUri uri = new PulsarUri("pulsar://persistent/tenant/namespace/topic");
        assertThat(uri.getTenant(), is("tenant"));
        assertThat(uri.getNamespace(), is("namespace"));
        assertThat(uri.getTopic(), is("topic"));
    }
}
