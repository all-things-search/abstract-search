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

import de.picturesafe.search.spring.configuration.TestConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.TimeZone;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfiguration.class)
public abstract class AbstractTimeZoneRelatedTest {

    @Autowired
    @Qualifier("elasticsearchTimeZone")
    protected String timeZone;

    private TimeZone tzBeforeTest;

    @Before
    public final void setTimeZone() {
        tzBeforeTest = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    }

    @After
    public final void resetTimeZone() {
        TimeZone.setDefault(tzBeforeTest);
    }
}