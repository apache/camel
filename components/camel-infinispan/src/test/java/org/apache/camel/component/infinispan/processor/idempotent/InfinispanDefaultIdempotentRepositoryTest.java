package org.apache.camel.component.infinispan.processor.idempotent;

import org.junit.Test;

import static org.jgroups.util.Util.assertFalse;
import static org.jgroups.util.Util.assertTrue;

public class InfinispanDefaultIdempotentRepositoryTest {

    @Test
    public void createsRepositoryUsingInternalCache() throws Exception {
        InfinispanIdempotentRepository repository = InfinispanIdempotentRepository.infinispanIdempotentRepository();

        assertFalse(repository.contains("One"));
        assertFalse(repository.remove("One"));

        assertTrue(repository.add("One"));

        assertTrue(repository.contains("One"));
        assertTrue(repository.remove("One"));

        assertFalse(repository.contains("One"));
        assertFalse(repository.remove("One"));
    }
}
