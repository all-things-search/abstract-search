/*
 * Copyright 2020 picturesafe media/data/bank GmbH
 */

package de.picturesafe.search.elasticsearch.connect.filter;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

public class InternalPhraseMatchFilterBuilder implements InternalFilterBuilder {

    @Override
    public QueryBuilder build(String key, Object value) {
        return QueryBuilders.matchPhraseQuery(key, value);
    }
}
