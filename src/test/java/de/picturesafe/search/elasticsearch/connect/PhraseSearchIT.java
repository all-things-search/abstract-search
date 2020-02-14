/*
 * Copyright 2020 picturesafe media/data/bank GmbH
 */

package de.picturesafe.search.elasticsearch.connect;

import de.picturesafe.search.elasticsearch.config.MappingConfiguration;
import de.picturesafe.search.elasticsearch.connect.dto.QueryDto;
import de.picturesafe.search.elasticsearch.connect.dto.QueryFilterDto;
import de.picturesafe.search.elasticsearch.connect.dto.QueryRangeDto;
import de.picturesafe.search.elasticsearch.connect.support.IndexSetup;
import de.picturesafe.search.expression.ValueExpression;
import de.picturesafe.search.parameter.SortOption;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PhraseSearchIT extends AbstractElasticIntegrationTest {

    private static final String[] TERMS = {"dies", "das", "dies und das"};

    @Autowired
    IndexSetup indexSetup;

    @Autowired
    MappingConfiguration mappingConfiguration;

    @Autowired
    Elasticsearch elasticsearch;

    @Before
    public void begin() throws Exception {
        indexSetup.setupIndex(indexAlias);

        final Map<String, Object> document = new HashMap<>();
        int i = 1;
        for (String keyword : TERMS) {
            document.put("id", i++);
            document.put("keyword", keyword);
            elasticsearch.addToIndex(document, mappingConfiguration, indexAlias, true);
        }
    }

    @After
    public void end() {
        indexSetup.tearDownIndex(indexAlias);
    }

    /**
     * Testet Suche nach Umlauten im Feld caption
     */
    @Test
    public void searchUmlautsInField() {
        assertEquals(2, phraseSearch("dies").getTotalHitCount());
        assertEquals(1, phraseSearch("dies und das").getTotalHitCount());
        assertEquals(1, phraseSearch("dies und").getTotalHitCount());
        assertEquals(1, phraseSearch("und das").getTotalHitCount());
        assertEquals(0, phraseSearch("dies das").getTotalHitCount());
    }

    private ElasticsearchResult phraseSearch(String term) {
        final ValueExpression expression = new ValueExpression("keyword", term);
        expression.setMatchPhrase(true);
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        final List<QueryFilterDto> queryFilterDtos = new ArrayList<>();
        final List<SortOption> sortOptionList = new ArrayList<>();
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, queryFilterDtos, sortOptionList, null, Locale.GERMAN);

        return elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
    }
}
