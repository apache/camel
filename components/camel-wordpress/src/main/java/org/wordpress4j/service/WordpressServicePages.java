package org.wordpress4j.service;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.Page;
import org.wordpress4j.model.PageSearchCriteria;

public interface WordpressServicePages extends WordpressCrudService<Page, PageSearchCriteria> {

    Page retrieve(Integer pageId, Context context, String password);

}
