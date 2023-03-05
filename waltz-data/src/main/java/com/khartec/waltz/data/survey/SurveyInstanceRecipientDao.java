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

package com.khartec.waltz.data.survey;

import com.khartec.waltz.data.person.PersonDao;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.survey.*;
import com.khartec.waltz.schema.tables.records.SurveyInstanceRecipientRecord;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.schema.Tables.*;

@Repository
public class SurveyInstanceRecipientDao {
    private static final RecordMapper<Record, SurveyInstanceRecipient> TO_DOMAIN_MAPPER = record ->
            ImmutableSurveyInstanceRecipient.builder()
                    .id(record.getValue(SURVEY_INSTANCE_RECIPIENT.ID))
                    .surveyInstance(ImmutableSurveyInstance.builder()
                            .id(record.getValue(SURVEY_INSTANCE.ID))
                            .surveyRunId(record.getValue(SURVEY_INSTANCE.SURVEY_RUN_ID))
                            .surveyEntity(EntityReference.mkRef(
                                    EntityKind.valueOf(record.getValue(SURVEY_INSTANCE.ENTITY_KIND)),
                                    record.getValue(SURVEY_INSTANCE.ENTITY_ID)))
                            .status(SurveyInstanceStatus.valueOf(record.getValue(SURVEY_INSTANCE.STATUS)))
                            .dueDate(record.getValue(SURVEY_INSTANCE.DUE_DATE).toLocalDate())
                            .build())
                    .person(PersonDao.personMapper.map(record))
                    .build();

    private final DSLContext dsl;


    @Autowired
    public SurveyInstanceRecipientDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");

        this.dsl = dsl;
    }


    public boolean isPersonInstanceRecipient(long personId, long surveyInstanceId) {
        Condition recipientExists = DSL.exists(DSL.selectFrom(SURVEY_INSTANCE_RECIPIENT)
                .where(SURVEY_INSTANCE_RECIPIENT.SURVEY_INSTANCE_ID.eq(surveyInstanceId)
                        .and(SURVEY_INSTANCE_RECIPIENT.PERSON_ID.eq(personId))));

        return dsl.select(DSL.when(recipientExists, true).otherwise(false))
                .fetchOne(Record1::value1);
    }


    public long create(SurveyInstanceRecipientCreateCommand command) {
        checkNotNull(command, "command cannot be null");

        SurveyInstanceRecipientRecord record = dsl.newRecord(SURVEY_INSTANCE_RECIPIENT);
        record.setSurveyInstanceId(command.surveyInstanceId());
        record.setPersonId(command.personId());

        record.store();
        return record.getId();
    }


    public boolean delete(long surveyInstanceRecipientId) {

        return dsl.deleteFrom(SURVEY_INSTANCE_RECIPIENT)
                .where(SURVEY_INSTANCE_RECIPIENT.ID.eq(surveyInstanceRecipientId))
                .execute() == 1;
    }


    public int deleteForSurveyRun(long surveyRunId) {
        Select<Record1<Long>> surveyInstanceIdSelector = dsl.select(SURVEY_INSTANCE.ID)
                .from(SURVEY_INSTANCE)
                .where(SURVEY_INSTANCE.SURVEY_RUN_ID.eq(surveyRunId));

        return dsl.delete(SURVEY_INSTANCE_RECIPIENT)
                .where(SURVEY_INSTANCE_RECIPIENT.SURVEY_INSTANCE_ID.in(surveyInstanceIdSelector))
                .execute();
    }


    public List<SurveyInstanceRecipient> findForSurveyInstance(long surveyInstanceId) {
        return dsl
                .select(SURVEY_INSTANCE_RECIPIENT.fields())
                .select(SURVEY_INSTANCE.fields())
                .select(PERSON.fields())
                .from(SURVEY_INSTANCE_RECIPIENT)
                .innerJoin(SURVEY_INSTANCE).on(SURVEY_INSTANCE.ID.eq(SURVEY_INSTANCE_RECIPIENT.SURVEY_INSTANCE_ID))
                .innerJoin(PERSON).on(PERSON.ID.eq(SURVEY_INSTANCE_RECIPIENT.PERSON_ID))
                .where(SURVEY_INSTANCE_RECIPIENT.SURVEY_INSTANCE_ID.eq(surveyInstanceId))
                .fetch(TO_DOMAIN_MAPPER);
    }
}
