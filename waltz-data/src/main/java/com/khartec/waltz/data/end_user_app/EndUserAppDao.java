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

package com.khartec.waltz.data.end_user_app;

import com.khartec.waltz.data.JooqUtilities;
import com.khartec.waltz.model.Criticality;
import com.khartec.waltz.model.application.LifecyclePhase;
import com.khartec.waltz.model.enduserapp.EndUserApplication;
import com.khartec.waltz.model.enduserapp.ImmutableEndUserApplication;
import com.khartec.waltz.model.tally.Tally;
import com.khartec.waltz.schema.tables.records.EndUserApplicationRecord;
import org.jooq.*;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.khartec.waltz.common.StringUtilities.mkSafe;
import static com.khartec.waltz.schema.tables.EndUserApplication.END_USER_APPLICATION;
import static java.util.Optional.ofNullable;

@Repository
public class EndUserAppDao {


    private final DSLContext dsl;

    public static final RecordMapper<Record, EndUserApplication> TO_DOMAIN_MAPPER = r -> {
        EndUserApplicationRecord record = r.into(END_USER_APPLICATION);
        return ImmutableEndUserApplication.builder()
                .name(record.getName())
                .description(mkSafe(record.getDescription()))
                .externalId(ofNullable(record.getExternalId()))
                .applicationKind(record.getKind())
                .id(record.getId())
                .organisationalUnitId(record.getOrganisationalUnitId())
                .lifecyclePhase(LifecyclePhase.valueOf(record.getLifecyclePhase()))
                .riskRating(Criticality.valueOf(record.getRiskRating()))
                .provenance(record.getProvenance())
                .isPromoted(record.getIsPromoted())
                .build();
    };

    @Autowired
    public EndUserAppDao(DSLContext dsl) {
        this.dsl = dsl;
    }


    public List<Tally<Long>> countByOrganisationalUnit() {
        return JooqUtilities.calculateLongTallies(
                dsl,
                END_USER_APPLICATION,
                END_USER_APPLICATION.ORGANISATIONAL_UNIT_ID,
                END_USER_APPLICATION.IS_PROMOTED.isFalse());
    }

    @Deprecated
    public List<EndUserApplication> findByOrganisationalUnitSelector(Select<Record1<Long>> selector) {
        return dsl.select(END_USER_APPLICATION.fields())
                .from(END_USER_APPLICATION)
                .where(END_USER_APPLICATION.ORGANISATIONAL_UNIT_ID.in(selector)
                        .and(END_USER_APPLICATION.IS_PROMOTED.isFalse()))
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<EndUserApplication> findBySelector(Select<Record1<Long>> selector) {
        return dsl.selectFrom(END_USER_APPLICATION)
                .where(END_USER_APPLICATION.ID.in(selector)
                        .and(END_USER_APPLICATION.IS_PROMOTED.isFalse()))
                .fetch(TO_DOMAIN_MAPPER);
    }


    public int updateIsPromotedFlag(long id) {
        return dsl.update(END_USER_APPLICATION)
                .set(END_USER_APPLICATION.IS_PROMOTED, true)
                .where(END_USER_APPLICATION.ID.eq(id))
                .execute();
    }


    public EndUserApplication getById(Long id) {
        return dsl
                .selectFrom(END_USER_APPLICATION)
                .where(END_USER_APPLICATION.ID.eq(id))
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    public List<EndUserApplication> findAll() {
        return dsl
                .selectFrom(END_USER_APPLICATION)
                .where(END_USER_APPLICATION.IS_PROMOTED.isFalse())
                .fetch(TO_DOMAIN_MAPPER);
    }
}
