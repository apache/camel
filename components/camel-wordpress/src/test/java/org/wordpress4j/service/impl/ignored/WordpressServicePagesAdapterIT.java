package org.wordpress4j.service.impl.ignored;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.apache.camel.component.wordpress.WordpressTestConstants;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.Page;
import org.wordpress4j.model.PageSearchCriteria;
import org.wordpress4j.service.WordpressServicePages;

@Ignore("Not implemented yet")
public class WordpressServicePagesAdapterIT {

    private static WordpressServicePages servicePages;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(WordpressTestConstants.WORDPRESS_DEMO_URL);
        servicePages = serviceProvider.getService(WordpressServicePages.class);
    }

    @Test
    public void testRetrieve() {
        final Page page = servicePages.retrieve(2, null, null);
        assertThat(page, not(nullValue()));
        assertThat(page.getId(), is(2));
    }

    @Test
    public void testList() {
        final PageSearchCriteria criteria = new PageSearchCriteria();
        criteria.setPage(1);
        criteria.setPerPage(5);
        final List<Page> posts = servicePages.list(criteria);
        assertThat(posts, is(not(emptyCollectionOf(Page.class))));
        assertThat(posts.size(), is(5));
    }
}
