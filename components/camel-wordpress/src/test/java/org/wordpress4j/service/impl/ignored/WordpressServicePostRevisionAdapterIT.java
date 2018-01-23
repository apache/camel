package org.wordpress4j.service.impl.ignored;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.apache.camel.component.wordpress.WordpressTestConstants;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.wordpress4j.WordpressAPIConfiguration;
import org.wordpress4j.WordpressConstants;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.auth.WordpressAuthentication;
import org.wordpress4j.auth.WordpressBasicAuthentication;
import org.wordpress4j.model.PostRevision;
import org.wordpress4j.service.WordpressServicePostRevision;
import org.wordpress4j.test.WordpressMockServerTestSupport;

/*
 * TODO fix authentication problem (when implementing global authentication) 
 * javax.ws.rs.NotAuthorizedException: HTTP 401 Unauthorized
 */
@Ignore("Not implemented yet")
public class WordpressServicePostRevisionAdapterIT extends WordpressMockServerTestSupport {

    private static WordpressServicePostRevision servicePostRevision;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        final WordpressAuthentication authentication = new WordpressBasicAuthentication("integration_test", "JD)e)Ox)z@HyDF*Dv4aWszm*");
        final WordpressAPIConfiguration configuration = 
            new WordpressAPIConfiguration(WordpressTestConstants.WORDPRESS4J__URL, WordpressConstants.API_VERSION);
        configuration.setAuthentication(authentication);
        serviceProvider.init(configuration);
        servicePostRevision = serviceProvider.getService(WordpressServicePostRevision.class);
    }

    @Test
    public void testRetrieve() {
        final PostRevision revision = servicePostRevision.retrieve(1, 1, null);
        assertThat(revision, not(nullValue()));
        assertThat(revision.getId(), is(1));
        assertThat(revision.getGuid(), notNullValue());
    }

    @Test
    public void testList() {
        final List<PostRevision> revisions = servicePostRevision.list(1, null);
        assertThat(revisions, is(not(emptyCollectionOf(PostRevision.class))));
        assertThat(revisions.size(), greaterThan(0));
    }
}
