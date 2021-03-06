/*
 * Copyright 2020 picturesafe media/data/bank GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.picturesafe.search.elasticsearch.connect;

import com.jayway.jsonpath.JsonPath;
import de.picturesafe.search.elasticsearch.config.MappingConfiguration;
import de.picturesafe.search.elasticsearch.connect.dto.FacetDto;
import de.picturesafe.search.elasticsearch.connect.dto.QueryDto;
import de.picturesafe.search.elasticsearch.connect.dto.QueryRangeDto;
import de.picturesafe.search.elasticsearch.connect.dto.SearchHitDto;
import de.picturesafe.search.elasticsearch.connect.dto.SearchResultDto;
import de.picturesafe.search.elasticsearch.connect.support.IndexSetup;
import de.picturesafe.search.elasticsearch.model.DocumentBuilder;
import de.picturesafe.search.expression.DayRangeExpression;
import de.picturesafe.search.expression.Expression;
import de.picturesafe.search.expression.FulltextExpression;
import de.picturesafe.search.expression.IsNullExpression;
import de.picturesafe.search.expression.KeywordExpression;
import de.picturesafe.search.expression.MustNotExpression;
import de.picturesafe.search.expression.OperationExpression;
import de.picturesafe.search.expression.ValueExpression;
import de.picturesafe.search.parameter.SearchParameter;
import de.picturesafe.search.parameter.SortOption;
import de.picturesafe.search.parameter.aggregation.DefaultAggregation;
import org.apache.commons.lang3.time.DateUtils;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.picturesafe.search.expression.ConditionExpression.Comparison.GE;
import static de.picturesafe.search.expression.ConditionExpression.Comparison.LE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BaseIT extends AbstractElasticIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseIT.class);

    @Autowired
    IndexSetup indexSetup;

    @Autowired
    MappingConfiguration mappingConfiguration;

    @Autowired
    Elasticsearch elasticsearch;

    @Before
    public void setup() {
        indexSetup.createIndex(indexAlias);

        final List<Map<String, Object>> docs = Arrays.asList(
            DocumentBuilder.id(1).put("title.de", "erster wert 1").put("caption", "caption1").put("facetResolved", "1")
                .put("location", "Hamburg Altona").build(),
            DocumentBuilder.id(2).put("title.de", "zweiter wert 2").put("caption", "caption2").put("facetResolved", "2")
                .put("location", "Bremen").build(),
            DocumentBuilder.id(3).put("title.de", "dritter wert 3").put("caption", "caption2").put("facetResolved", "3")
                .put("location", "Rostock").build(),
            DocumentBuilder.id(4).put("title.de", "vierter wert 4").put("caption", "Schleswig-Holstein liegt im Norden").put("facetResolved", "4")
                .put("location", "Bosnien Herzegowina").put("createDate", problemDay()).build(),
            DocumentBuilder.id(5).put("title.de", "fünfter wert 5").put("caption", "Schleswig liegt nicht in Holstein").put("facetResolved", "5")
                .put("createDate", today()).build(),
            DocumentBuilder.id(6).put("title.de", "Released").put("caption", "Record released").put("released", true)
                .build(),
            DocumentBuilder.id(7).put("title.de", "Not released").put("caption", "Record not released").put("released", false)
                .build());
        elasticsearch.addToIndex(indexAlias, true, true, docs);
    }

    @After
    public void tearDown() {
        indexSetup.tearDownIndex(indexAlias);
    }

    @Test
    public void testAdd() throws IOException {
        final Map<String, Object> data = new HashMap<>();
        data.put("id", "15");
        elasticsearch.addToIndex(indexAlias, true, data);
        GetResponse res = restClient.get(new GetRequest().index(indexAlias).id("15"), RequestOptions.DEFAULT);

        assertTrue("Cannot find required document: indexAlias = " + indexAlias, res.isExists());

        res = restClient.get(new GetRequest().index(indexAlias).id("10"), RequestOptions.DEFAULT);
        assertFalse("Found document that was not added to index: indexAlias = " + indexAlias, res.isExists());
    }

    @Test
    public void testSearch() {
        final SearchResultDto shouldBeFound = defaultSearch("cAption1");
        assertEquals("Can not find required value in index: indexAlias = " + indexAlias, 1, shouldBeFound.getTotalHitCount());

        final SearchResultDto mustNotBeFound = defaultSearch("illegalValue");
        assertEquals("Found value that was not added to index: indexAlias = " + indexAlias, 0, mustNotBeFound.getTotalHitCount());
    }

    private SearchResultDto defaultSearch(String value) {
        LOG.debug("Searching fulltext: indexAlias = {}, value = {}", indexAlias, value);
        final Expression expression = new FulltextExpression(value);
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        return elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
    }

    private QueryRangeDto defaultRange() {
        return new QueryRangeDto(0, 40);
    }

    @Test
    public void testCreateQueryJson() {
        final Expression expression = new FulltextExpression("caption*");
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final String json = elasticsearch.createQueryJson(queryDto, mappingConfiguration, indexPresetConfiguration, true);
        LOG.debug("JSON:\n{}", json);
        assertEquals("caption*", JsonPath.read(json, "$.query.query_string.query"));
    }

    @Test
    public void testAndExpression() {
        final OperationExpression andExpression = OperationExpression.and(
                new FulltextExpression("wert"),
                new ValueExpression("caption", "caption1"));
        final QueryDto queryDto = new QueryDto(andExpression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testFieldExpression() {
        final Expression expression = new ValueExpression("caption", "caption1");
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testDateExpression() {
        final Expression expression = new DayRangeExpression("createDate", yesterday(), tomorrow());
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testTodayExpression() {
        final Expression expression = new DayRangeExpression("createDate", today(), today());
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testDateExpression1923() throws Exception {
        // Problematisches Datum: 01.01.1923
        final Date thatday = DateUtils.parseDate("01.01.1923", STANDARD_DATE_FORMAT);
        final Date from = DateUtils.addDays(thatday, -1);
        final Date until = DateUtils.addDays(thatday, +1);
        final Expression expression = new DayRangeExpression("createDate", from, until);
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testDayExpression1923() throws Exception {
        // Problematisches Datum: 01.01.1923
        final Date thatday = DateUtils.parseDate("01.01.1923", STANDARD_DATE_FORMAT);
        final Expression expression = new DayRangeExpression("createDate", thatday, thatday);
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testFieldExpressionWithoutLocale() {
        final Expression expression = new ValueExpression("caption", "caption1");
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, null);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testFieldExpressionWithoutLocaleOnMultiligual() {
        final Expression expression = new ValueExpression("title", "erster");
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, null);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 0, result.getTotalHitCount());
    }

    @Test
    public void testFulltextExpression() {
        final SearchResultDto result = defaultSearch("wert");

        assertEquals("Expression not working: indexAlias = " + indexAlias, 5, result.getTotalHitCount());
    }

    @Test
    public void testExpressionOnMultiple() {
        final Expression expression = new ValueExpression("title", "erster");
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Expression not working: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testDescSortOrder() {
        final Expression expression = new ValueExpression("id", LE, 3);
        final List<SortOption> sortOptions = Collections.singletonList(SortOption.desc("facetResolved"));
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), sortOptions, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Desc sort option not working: indexAlias = " + indexAlias, "3", result.getHits().get(0).get("facetResolved"));
        assertEquals("Desc sort option not working: indexAlias = " + indexAlias, "1", result.getHits().get(2).get("facetResolved"));
    }

    @Test
    public void testAscSortOrder() {
        final Expression expression = new ValueExpression("id", LE, 3);
        final List<SortOption> sortOptions = Collections.singletonList(SortOption.asc("facetResolved"));
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), sortOptions, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Asc sort option not working: indexAlias = " + indexAlias, "1", result.getHits().get(0).get("facetResolved"));
        assertEquals("Asc sort option not working: indexAlias = " + indexAlias, "3", result.getHits().get(2).get("facetResolved"));
    }

    @Test
    public void testNonSupportedSortField() {
        final Expression expression = new FulltextExpression("wert");
        final List<SortOption> sortOptions = Collections.singletonList(SortOption.asc("caption"));
        final QueryDto queryDto = new QueryDto(expression, defaultRange(), sortOptions, null, Locale.GERMAN);

        Exception exception = null;
        try {
            elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        } catch (Exception e) {
            exception = e;
        }

        assertNotNull("Should throw exception for non supported sort field: indexAlias = " + indexAlias, exception);
        assertEquals("Should throw exception for non supported sort field: indexAlias = " + indexAlias,
                "The field 'caption' is not configured as sortable!", exception.getMessage());
    }

    @Test
    public void testQueryRange() {
        final Expression expression = new FulltextExpression("wert");
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 1);
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Result should contain 1 value: indexAlias = " + indexAlias, 1, result.getHits().size());
    }

    @Test
    public void testDelete() {
        final Expression expression = null;
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        final List<SortOption> sortOptionList = new ArrayList<>();
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);

        SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        LOG.debug("result.getCount()=" + result.getTotalHitCount());

        assertEquals("There should be exactly 7 results: indexAlias = " + indexAlias, 7, result.getTotalHitCount());

        elasticsearch.removeFromIndex(indexAlias, true, Arrays.asList(2L, 3L));

        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        LOG.debug("result.getCount()=" + result.getTotalHitCount());

        assertEquals("There should be 5 results left: indexAlias = " + indexAlias, 5, result.getTotalHitCount());
    }


    @Test
    public void testCompareCondition() {
        final Expression expression = new ValueExpression("id", GE, 2);
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        final List<SortOption> sortOptionList = new ArrayList<>();
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("There should be 6 results: indexAlias = " + indexAlias, 6, result.getTotalHitCount());
    }

    @Test
    public void testPhraseSearch() {
        final Expression expression = new FulltextExpression("{zweiter wert}");
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        final List<SortOption> sortOptionList = new ArrayList<>();
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("There should 1 result: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }


    @Test
    public void testPath() throws IOException {
        elasticsearch.addToIndex(indexAlias, true, addTreePathDocument("1", "Bla/Bli/Blub"));
        elasticsearch.addToIndex(indexAlias, true, addTreePathDocument("2", "Bla/Bli/Bla"));
        elasticsearch.addToIndex(indexAlias, true, addTreePathDocument("3", "Rums/Bums"));
        restClient.indices().refresh(new RefreshRequest().indices(indexAlias), RequestOptions.DEFAULT);

        LOG.debug("Document 1: " + restClient.get(new GetRequest().index(indexAlias).id("1"),
                                                  RequestOptions.DEFAULT).getSourceAsString());
        LOG.debug("Document 1: " + restClient.get(new GetRequest().index(indexAlias).id("2"),
                                                  RequestOptions.DEFAULT).getSourceAsString());
        LOG.debug("Document 1: " + restClient.get(new GetRequest().index(indexAlias).id("3"),
                                                  RequestOptions.DEFAULT).getSourceAsString());

        final ValueExpression valueExpression = new ValueExpression("treePaths", "Bla\\/Bli*");
        final QueryDto queryDto = new QueryDto(valueExpression, new QueryRangeDto(0, 10), null, null,
                Locale.GERMAN);
        final SearchResultDto searchResultDto = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        LOG.debug(searchResultDto.toString());
        assertEquals("Should find 2 documents for path Bla/Bli: indexAlias = " + indexAlias, 2, searchResultDto.getTotalHitCount());

        final ValueExpression valueExpression2 = new ValueExpression("treePaths", "Bla\\/Bli\\/Blub*");
        final QueryDto queryDto2 = new QueryDto(valueExpression2, new QueryRangeDto(0, 10), null, null,
                Locale.GERMAN);
        final SearchResultDto searchResultDto2 = elasticsearch.search(queryDto2, mappingConfiguration, indexPresetConfiguration);

        LOG.debug(searchResultDto2.toString());
        assertEquals("Should find 2 documents for path Bla/Bli/Blub: indexAlias = " + indexAlias, 1, searchResultDto2.getTotalHitCount());

        final ValueExpression valueExpression3 = new ValueExpression("treePaths", "Rums*");
        final QueryDto queryDto3 = new QueryDto(valueExpression3, new QueryRangeDto(0, 10), null, null,
                Locale.GERMAN);
        final SearchResultDto searchResultDto3 = elasticsearch.search(queryDto3, mappingConfiguration, indexPresetConfiguration);

        LOG.debug(searchResultDto3.toString());
        assertEquals("Should find 1 document for path Rums: indexAlias = " + indexAlias, 1, searchResultDto3.getTotalHitCount());
    }

    @Test
    public void testKeywordField() throws IOException {
        final List<Map<String, Object>> docs = Arrays.asList(
                DocumentBuilder.id(55).put("caption", "Familie Meier").put("keywordField", "hans meier").build(),
                DocumentBuilder.id(56).put("caption", "Familie Meier").put("keywordField", "margarete meier").build()
        );
        elasticsearch.addToIndex(indexAlias, true, true, docs);
        LOG.debug("Document 1:\n" + restClient.get(new GetRequest().index(indexAlias).id("55"), RequestOptions.DEFAULT).getSourceAsString());

        Expression expression = new ValueExpression("keywordField", "hans meier");
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        QueryDto queryDto = new QueryDto(expression, queryRangeDto, null, null, null);
        SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Should find document with keyword query: indexAlias = " + indexAlias, 1, result.getTotalHitCount());

        expression = new FulltextExpression("meier");
        final SortOption sortOption = SortOption.desc("keywordField");
        final DefaultAggregation aggregation = DefaultAggregation.field("keywordField");
        queryDto = new QueryDto(expression, queryRangeDto, Collections.singletonList(sortOption), Collections.singletonList(aggregation), null);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Should find documents of 'Famile Meier'': indexAlias = " + indexAlias, 2, result.getTotalHitCount());
        assertEquals("margarete meier", result.getHits().get(0).get("keywordField"));
        assertEquals("hans meier", result.getHits().get(1).get("keywordField"));
        assertEquals(1, result.getFacetDtoList().size());
        final FacetDto facetDto = result.getFacetDtoList().get(0);
        assertEquals("keywordField", facetDto.getName());
        assertEquals("keywordField", facetDto.getFieldName());
        assertEquals(2, facetDto.getCount());

    }

    @Test
    public void testColon() throws IOException {
        final Map<String, Object> document = new HashMap<>();
        document.put("id", "55");
        document.put("keywordField", "urn:newsml:dpa.com:20090101:121024-99-04786");
        elasticsearch.addToIndex(indexAlias, true, document);
        LOG.debug("Document 1: " + restClient.get(new GetRequest().index(indexAlias).id("55"),
                                                  RequestOptions.DEFAULT).getSourceAsString());

        final ValueExpression valueExpression = new ValueExpression(
                "keywordField",
                "urn:newsml:dpa.com:20090101:121024-99-04786"
            );
        valueExpression.setMatchPhrase(true);
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        final QueryDto queryDto = new QueryDto(valueExpression, queryRangeDto, null, null, null);
        final SearchResultDto searchResultDto = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertEquals("Should find document with keyword query: indexAlias = " + indexAlias, 1, searchResultDto.getTotalHitCount());
    }

    @Test
    public void testAddToAll() {
        final SearchResultDto searchResultDto = defaultSearch("system1");
        assertEquals("Non all field value should not be found: indexAlias = " + indexAlias, 0, searchResultDto.getTotalHitCount());
    }

    @Test
    public void testIsNullExpression() {
        final Map<String, Object> document = new HashMap<>();
        document.put("id", "99");
        elasticsearch.addToIndex(indexAlias, true, document);

        Expression expression = new IsNullExpression("caption", true);
        SearchResultDto searchResultDto = elasticsearch.search(
                new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN),
                mappingConfiguration, indexPresetConfiguration
            );
        assertEquals("Should find one document without caption: indexAlias = " + indexAlias, 1, searchResultDto.getTotalHitCount());

        expression = new IsNullExpression("caption", false);
        searchResultDto = elasticsearch.search(
                new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN),
                mappingConfiguration, indexPresetConfiguration
            );
        assertEquals("Should find 7 documents without caption: indexAlias = " + indexAlias, 7, searchResultDto.getTotalHitCount());
    }

    @Test
    public void testDocValuesFieldsToResolve() {
        final Expression expression = new FulltextExpression("wert");
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 1000);
        final List<String> fieldsToResolve = new ArrayList<>();
        fieldsToResolve.add("caption");
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, null, null,
                null, fieldsToResolve, QueryDto.FieldResolverType.DOC_VALUES);
        final SearchResultDto results = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertTrue("There must be search results to test: indexAlias = " + indexAlias, results.getTotalHitCount() > 0);
        for (SearchHitDto hit : results.getHits()) {
            assertEquals("There must be only one field in the search hit: indexAlias = " + indexAlias, 1, hit.size());
            assertNotNull("The id must not be null: indexAlias = " + indexAlias, hit.getId());
            assertNotNull(hit.get("caption.keyword"));
        }
    }

    @Test
    public void testSourceValuesFieldsToResolve() {
        final List<String> fieldsToResolve = new ArrayList<>();
        fieldsToResolve.add("caption");

        final SearchParameter searchParameter = SearchParameter.builder().fieldsToResolve(fieldsToResolve).build();
        final Expression expression = new FulltextExpression("wert");
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 1000);
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, null, null,
                null, searchParameter.getFieldsToResolve(), QueryDto.FieldResolverType.SOURCE_VALUES);
        final SearchResultDto results = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertTrue("There must be search results to test: indexAlias = " + indexAlias, results.getTotalHitCount() > 0);
        for (SearchHitDto hit : results.getHits()) {
            assertEquals("There must be only one field in the search hit: indexAlias = " + indexAlias, 1, hit.size());
            assertNotNull("The id must not be null: indexAlias = " + indexAlias, hit.getId());
            assertNotNull(hit.get("caption"));
        }
    }

    @Test
    public void testIdSourceValueFieldToResolve() {
        final List<String> fieldsToResolve = new ArrayList<>();
        fieldsToResolve.add("id");

        final SearchParameter searchParameter = SearchParameter.builder().fieldsToResolve(fieldsToResolve).build();
        final Expression expression = new FulltextExpression("wert");
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 1000);
        final QueryDto queryDto = new QueryDto(expression, queryRangeDto, null, null,
                null, searchParameter.getFieldsToResolve(), QueryDto.FieldResolverType.SOURCE_VALUES);
        final SearchResultDto results = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);

        assertTrue("There must be search results to test: indexAlias = " + indexAlias, results.getTotalHitCount() > 0);
        for (SearchHitDto hit : results.getHits()) {
            assertEquals("There must be only one field in a search result element: indexAlias = " + indexAlias, 1, hit.size());
            assertNotNull("The only field in a search result must be id: indexAlias = " + indexAlias, hit);
        }
    }

    private Map<String, Object> addTreePathDocument(String id, String path) {
        final Map<String, Object> document = new HashMap<>();
        document.put("id", id);
        document.put("treePaths", path);
        return document;
    }

    /**
     * Testet die Suchbarkeit von multi-fields
     */
    @Test
    public void testMultiFieldSearch() {
        final QueryRangeDto queryRangeDto = new QueryRangeDto(0, 10);
        final List<SortOption> sortOptionList = new ArrayList<>();

        Expression expression;
        QueryDto queryDto;
        SearchResultDto result;

        // 1. Search via single tokens in the whole document (_all)
        expression = new FulltextExpression("Bosnien");
        queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("There should be results: indexAlias = " + indexAlias, 1, result.getTotalHitCount());

        // 2. Search by value in the whole document (_all), phrase search
        expression = new FulltextExpression("{Bosnien Herzegowina}");
        queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("There should be results: indexAlias = " + indexAlias, 1, result.getTotalHitCount());

        // 3. Search via individual tokens in the field
        expression = new ValueExpression("location", "Bosnien");
        queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("There should be results: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
        // 4. Search by value in field, phrase search in field
        expression = new ValueExpression("location", "{Bosnien Herzegowina}");
        queryDto = new QueryDto(expression, queryRangeDto, sortOptionList, null, Locale.GERMAN);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("There should be results: indexAlias = " + indexAlias, 1, result.getTotalHitCount());
    }

    @Test
    public void testBoolean() {
        Expression expression = new ValueExpression("released", true);
        QueryDto queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Expression does not work: indexAlias = " + indexAlias, 1, result.getTotalHitCount());

        expression = new ValueExpression("released", false);
        queryDto = new QueryDto(expression, defaultRange(), null, null, Locale.GERMAN);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Expression does not work: indexAlias = " + indexAlias, 6, result.getTotalHitCount());
    }

    @Test
    public void testMustNotExpression() {
        OperationExpression operationExpression = OperationExpression.and(
                new ValueExpression("title", "wert"),
                new MustNotExpression(new ValueExpression("caption", "caption1")));
        QueryDto queryDto = new QueryDto(operationExpression, defaultRange(), null, null, Locale.GERMAN);
        SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Expression does not work: indexAlias = " + indexAlias, 4, result.getTotalHitCount());

        final OperationExpression innerOpExpression = OperationExpression.and(new ValueExpression("caption", "caption1"));
        final MustNotExpression mustNotExpression = new MustNotExpression(innerOpExpression);
        operationExpression = OperationExpression.and(new ValueExpression("title", "wert"), mustNotExpression);
        queryDto = new QueryDto(operationExpression, defaultRange(), null, null, Locale.GERMAN);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Expression does not work: indexAlias = " + indexAlias, 4, result.getTotalHitCount());
    }

    @Test
    public void testFulltextWithInnerMustNotExpression() {
        final OperationExpression innerOpExpression = OperationExpression.and(
                new KeywordExpression("caption", "caption1"));
        final OperationExpression operationExpression = OperationExpression.and(
                new FulltextExpression("wert"), new MustNotExpression(innerOpExpression));

        final QueryDto queryDto = new QueryDto(operationExpression, defaultRange(), null, null, Locale.GERMAN);
        final SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals("Expression does not work: indexAlias = " + indexAlias, 4, result.getTotalHitCount());
    }

    @Test
    public void testRemoveByQuery() {
        final List<Map<String, Object>> docs = Arrays.asList(
            DocumentBuilder.id(101).put("title.de", "Released").put("caption", "Record released").put("released", true)
                .build(),
            DocumentBuilder.id(102).put("title.de", "Released").put("caption", "Record not released").put("released", true)
                .build(),
            DocumentBuilder.id(103).put("title.de", "Not released").put("caption", "Record not released").put("released", false)
                .build());
        elasticsearch.addToIndex(indexAlias, true, true, docs);

        final QueryDto queryDto = new QueryDto(new ValueExpression("released", true), Locale.ROOT);
        SearchResultDto result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals(3, result.getTotalHitCount());

        elasticsearch.removeFromIndex(queryDto, mappingConfiguration, indexPresetConfiguration, true);
        result = elasticsearch.search(queryDto, mappingConfiguration, indexPresetConfiguration);
        assertEquals(0, result.getTotalHitCount());
    }
}

