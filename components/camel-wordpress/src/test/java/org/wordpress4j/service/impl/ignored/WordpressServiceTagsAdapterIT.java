package org.wordpress4j.service.impl.ignored;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.apache.camel.component.wordpress.WordpressTestConstants;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.Tag;
import org.wordpress4j.model.TagSearchCriteria;
import org.wordpress4j.service.WordpressServiceTags;

@Ignore("Not implemented yet")
public class WordpressServiceTagsAdapterIT {

    private static WordpressServiceTags serviceTags;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(WordpressTestConstants.WORDPRESS_DEMO_URL);
        serviceTags = serviceProvider.getService(WordpressServiceTags.class);
    }

    @Test
    public void testRetrieve() {
        final Tag tag = serviceTags.retrieve(6, null);
        assertThat(tag, not(nullValue()));
        assertThat(tag.getId(), is(6));
        assertThat(tag.getName(), not(isEmptyOrNullString()));
    }

    @Test
    public void testList() {
        final TagSearchCriteria criteria = new TagSearchCriteria();
        criteria.setPage(1);
        criteria.setPerPage(2);
        final List<Tag> revisions = serviceTags.list(criteria);
        assertThat(revisions, is(not(emptyCollectionOf(Tag.class))));
        assertThat(revisions.size(), is(2));
    }
}
