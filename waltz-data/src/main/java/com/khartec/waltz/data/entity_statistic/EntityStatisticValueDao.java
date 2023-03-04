/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
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
 * See the License for the specific
 *
 */

package com.khartec.waltz.data.entity_statistic;

import com.khartec.waltz.data.application.ApplicationDao;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.ImmutableEntityReference;
import com.khartec.waltz.model.application.Application;
import com.khartec.waltz.model.entity_statistic.EntityStatisticValue;
import com.khartec.waltz.model.entity_statistic.ImmutableEntityStatisticValue;
import com.khartec.waltz.model.entity_statistic.StatisticValueState;
import com.khartec.waltz.schema.tables.records.EntityStatisticValueRecord;
import org.jooq.*;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.schema.tables.Application.APPLICATION;
import static com.khartec.waltz.schema.tables.EntityStatisticDefinition.ENTITY_STATISTIC_DEFINITION;
import static com.khartec.waltz.schema.tables.EntityStatisticValue.ENTITY_STATISTIC_VALUE;

@Repository
public class EntityStatisticValueDao {

    private static final com.khartec.waltz.schema.tables.EntityStatisticValue esv = ENTITY_STATISTIC_VALUE.as("esv");
    private static final com.khartec.waltz.schema.tables.Application app = APPLICATION.as("app");


    public static final RecordMapper<? super Record, EntityStatisticValue> TO_VALUE_MAPPER = r -> {
        EntityStatisticValueRecord record = r.into(ENTITY_STATISTIC_VALUE);
        return  ImmutableEntityStatisticValue.builder()
                .id(record.getId())
                .statisticId(record.getStatisticId())
                .entity(ImmutableEntityReference.builder()
                        .kind(EntityKind.valueOf(record.getEntityKind()))
                        .id(record.getEntityId())
                        .name(r.getValue(app.NAME))
                        .build())
                .value(record.getValue())
                .outcome(record.getOutcome())
                .state(StatisticValueState.valueOf(record.getState()))
                .reason(record.getReason())
                .createdAt(record.getCreatedAt().toLocalDateTime())
                .current(record.getCurrent())
                .provenance(record.getProvenance())
                .build();
    };


    private final DSLContext dsl;


    @Autowired
    public EntityStatisticValueDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");
        this.dsl = dsl;
    }


    public int[] bulkSaveValues(List<EntityStatisticValue> values) {
        return dsl
                .batch(values.stream()
                        .map(s -> dsl
                                .insertInto(
                                        ENTITY_STATISTIC_VALUE,
                                        ENTITY_STATISTIC_VALUE.STATISTIC_ID,
                                        ENTITY_STATISTIC_VALUE.ENTITY_KIND,
                                        ENTITY_STATISTIC_VALUE.ENTITY_ID,
                                        ENTITY_STATISTIC_VALUE.VALUE,
                                        ENTITY_STATISTIC_VALUE.OUTCOME,
                                        ENTITY_STATISTIC_VALUE.STATE,
                                        ENTITY_STATISTIC_VALUE.REASON,
                                        ENTITY_STATISTIC_VALUE.CREATED_AT,
                                        ENTITY_STATISTIC_VALUE.CURRENT,
                                        ENTITY_STATISTIC_VALUE.PROVENANCE)
                                .values(
                                        s.statisticId(),
                                        s.entity().kind().name(),
                                        s.entity().id(),
                                        s.value(),
                                        s.outcome(),
                                        s.state().name(),
                                        s.reason(),
                                        Timestamp.valueOf(s.createdAt()),
                                        s.current(),
                                        s.provenance()))
                        .collect(Collectors.toList()))
                .execute();
    }




    public List<EntityStatisticValue> getStatisticValuesForAppIdSelector(long statisticId, Select<Record1<Long>> appIdSelector) {
        checkNotNull(appIdSelector, "appIdSelector cannot be null");

        Condition condition = mkStatisticSelectorCondition(statisticId, appIdSelector);

        List<EntityStatisticValue> fetch = dsl
                .select(app.NAME)
                .select(esv.fields())
                .from(esv)
                .join(app)
                .on(esv.ENTITY_ID.eq(app.ID))
                .where(dsl.renderInlined(condition))
                .fetch(TO_VALUE_MAPPER);

        return fetch;
    }


    public List<Application> getStatisticAppsForAppIdSelector(long statisticId, Select<Record1<Long>> appIdSelector) {
        checkNotNull(appIdSelector, "appIdSelector cannot be null");

        Condition condition = mkStatisticSelectorCondition(statisticId, appIdSelector);

        return dsl
                .selectDistinct(app.fields())
                .from(app)
                .join(esv)
                .on(esv.ENTITY_ID.eq(app.ID))
                .where(dsl.renderInlined(condition))
                .fetch(ApplicationDao.TO_DOMAIN_MAPPER);
    }


    private Condition mkStatisticSelectorCondition(long statisticId, Select<Record1<Long>> appIdSelector) {
        return esv.STATISTIC_ID.eq(statisticId)
                    .and(esv.CURRENT.eq(true))
                    .and(esv.ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                    .and(esv.ENTITY_ID.in(appIdSelector));
    }

}