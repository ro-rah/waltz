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

package com.khartec.waltz.data.physical_flow;

import com.khartec.waltz.data.enum_value.EnumValueDao;
import com.khartec.waltz.model.*;
import com.khartec.waltz.model.enum_value.EnumValueKind;
import com.khartec.waltz.model.physical_flow.FrequencyKind;
import com.khartec.waltz.model.physical_flow.ImmutablePhysicalFlow;
import com.khartec.waltz.model.physical_flow.PhysicalFlow;
import com.khartec.waltz.model.physical_flow.PhysicalFlowParsed;
import com.khartec.waltz.schema.tables.records.PhysicalFlowRecord;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static com.khartec.waltz.common.Checks.checkFalse;
import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.DateTimeUtilities.nowUtcTimestamp;
import static com.khartec.waltz.common.EnumUtilities.readEnum;
import static com.khartec.waltz.data.logical_flow.LogicalFlowDao.LOGICAL_NOT_REMOVED;
import static com.khartec.waltz.model.EntityLifecycleStatus.REMOVED;
import static com.khartec.waltz.schema.Tables.EXTERNAL_IDENTIFIER;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static com.khartec.waltz.schema.tables.PhysicalFlow.PHYSICAL_FLOW;
import static com.khartec.waltz.schema.tables.PhysicalSpecDataType.PHYSICAL_SPEC_DATA_TYPE;
import static com.khartec.waltz.schema.tables.PhysicalSpecification.PHYSICAL_SPECIFICATION;


@Repository
public class PhysicalFlowDao {

    private static final Logger LOG = LoggerFactory.getLogger(PhysicalFlowDao.class);

    public static final RecordMapper<Record, PhysicalFlow> TO_DOMAIN_MAPPER = r -> {
        PhysicalFlowRecord record = r.into(PHYSICAL_FLOW);
        return ImmutablePhysicalFlow.builder()
                .id(record.getId())
                .provenance(record.getProvenance())
                .specificationId(record.getSpecificationId())
                .basisOffset(record.getBasisOffset())
                .frequency(FrequencyKind.valueOf(record.getFrequency()))
                .criticality(Criticality.valueOf(record.getCriticality()))
                .description(record.getDescription())
                .logicalFlowId(record.getLogicalFlowId())
                .transport(record.getTransport())
                .freshnessIndicator(readEnum(
                        record.getFreshnessIndicator(),
                        FreshnessIndicator.class,
                        x -> FreshnessIndicator.NEVER_OBSERVED))
                .specificationDefinitionId(Optional.ofNullable(record.getSpecificationDefinitionId()))
                .lastUpdatedBy(record.getLastUpdatedBy())
                .lastUpdatedAt(record.getLastUpdatedAt().toLocalDateTime())
                .lastAttestedBy(Optional.ofNullable(record.getLastAttestedBy()))
                .lastAttestedAt(Optional.ofNullable(record.getLastAttestedAt()).map(Timestamp::toLocalDateTime))
                .isRemoved(record.getIsRemoved())
                .externalId(Optional.ofNullable(record.getExternalId()))
                .entityLifecycleStatus(EntityLifecycleStatus.valueOf(record.getEntityLifecycleStatus()))
                .created(UserTimestamp.mkForUser(record.getCreatedBy(), record.getCreatedAt()))
                .build();
    };

    public static final Condition PHYSICAL_FLOW_NOT_REMOVED = PHYSICAL_FLOW.IS_REMOVED.isFalse()
            .and(PHYSICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(EntityLifecycleStatus.REMOVED.name()));


    private final DSLContext dsl;


    @Autowired
    public PhysicalFlowDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");
        this.dsl = dsl;
    }


    public List<PhysicalFlow> findByEntityReference(EntityReference ref) {
        checkNotNull(ref, "ref cannot be null");

        Select<Record> consumedFlows = findByConsumerEntityReferenceQuery(ref);
        Select<Record> producedFlows = findByProducerEntityReferenceQuery(ref);

        return consumedFlows
                .union(producedFlows)
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<PhysicalFlow> findByProducer(EntityReference ref) {
        return findByProducerEntityReferenceQuery(ref)
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<PhysicalFlow> findByExternalId(String externalId) {
        return dsl
                .selectDistinct(PHYSICAL_FLOW.fields())
                .from(PHYSICAL_FLOW)
                .leftJoin(EXTERNAL_IDENTIFIER)
                .on(EXTERNAL_IDENTIFIER.ENTITY_ID.eq(PHYSICAL_FLOW.ID)
                        .and(EXTERNAL_IDENTIFIER.ENTITY_KIND.eq(EntityKind.PHYSICAL_FLOW.name())))
                .where(PHYSICAL_FLOW.EXTERNAL_ID.eq(externalId).or(EXTERNAL_IDENTIFIER.EXTERNAL_ID.eq(externalId)))
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<PhysicalFlow> findByConsumer(EntityReference ref) {
        return findByConsumerEntityReferenceQuery(ref)
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<PhysicalFlow> findByProducerAndConsumer(EntityReference producer, EntityReference consumer) {
        return findByProducerAndConsumerEntityReferenceQuery(producer, consumer)
                .fetch(TO_DOMAIN_MAPPER);
    }


    public PhysicalFlow getById(long id) {
        return findByCondition(PHYSICAL_FLOW.ID.eq(id))
                .stream()
                .findFirst()
                .orElse(null);
    }


    public List<PhysicalFlow> findBySpecificationId(long specificationId) {
        return findByCondition(PHYSICAL_FLOW.SPECIFICATION_ID.eq(specificationId));
    }


    public List<PhysicalFlow> findBySelector(Select<Record1<Long>> selector) {
        return findByCondition(PHYSICAL_FLOW.ID.in(selector));
    }


    public List<PhysicalFlow> findByAttributesAndSpecification(PhysicalFlow flow) {

        Condition sameFlow = PHYSICAL_FLOW.SPECIFICATION_ID.eq(flow.specificationId())
                .and(PHYSICAL_FLOW.BASIS_OFFSET.eq(flow.basisOffset()))
                .and(PHYSICAL_FLOW.FREQUENCY.eq(flow.frequency().name()))
                .and(PHYSICAL_FLOW.TRANSPORT.eq(flow.transport()))
                .and(PHYSICAL_FLOW.LOGICAL_FLOW_ID.eq(flow.logicalFlowId()));

        return findByCondition(sameFlow);
    }


    public PhysicalFlow getByParsedFlow(PhysicalFlowParsed flow) {
        Condition attributesMatch = PHYSICAL_FLOW.BASIS_OFFSET.eq(flow.basisOffset())
                .and(PHYSICAL_FLOW.FREQUENCY.eq(flow.frequency().name()))
                .and(PHYSICAL_FLOW.TRANSPORT.eq(flow.transport()))
                .and(PHYSICAL_FLOW.CRITICALITY.eq(flow.criticality().name()))
                .and(PHYSICAL_FLOW_NOT_REMOVED);

        Condition logicalFlowMatch = LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(flow.source().id())
                .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(flow.source().kind().name()))
                .and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(flow.target().id()))
                .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(flow.target().kind().name()))
                .and(LOGICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name()));

        Condition specMatch = PHYSICAL_SPECIFICATION.OWNING_ENTITY_ID.eq(flow.owner().id())
                .and(PHYSICAL_SPECIFICATION.OWNING_ENTITY_KIND.eq(flow.owner().kind().name()))
                .and(PHYSICAL_SPECIFICATION.FORMAT.eq(flow.format().name()))
                .and(PHYSICAL_SPECIFICATION.NAME.eq(flow.name()))
                .and(PHYSICAL_SPECIFICATION.IS_REMOVED.isFalse());

        Condition specDataTypeMatch = PHYSICAL_SPEC_DATA_TYPE.DATA_TYPE_ID.eq(flow.dataType().id());


        return dsl
                .select(PHYSICAL_FLOW.fields())
                .from(PHYSICAL_FLOW)
                .join(LOGICAL_FLOW).on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .join(PHYSICAL_SPECIFICATION).on(PHYSICAL_SPECIFICATION.ID.eq(PHYSICAL_FLOW.SPECIFICATION_ID))
                .join(PHYSICAL_SPEC_DATA_TYPE).on(PHYSICAL_SPEC_DATA_TYPE.SPECIFICATION_ID.eq(PHYSICAL_SPECIFICATION.ID))
                .where(logicalFlowMatch)
                .and(specMatch)
                .and(attributesMatch)
                .and(specDataTypeMatch)
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    /**
     * Returns the flow in the database that matches the parameter based on all attributes except possibly id
     *
     * @param flow the physical flow to match against
     * @return matching flow or null
     */
    public PhysicalFlow matchPhysicalFlow(PhysicalFlow flow) {

        Condition idCondition = flow.id().isPresent()
                ? PHYSICAL_FLOW.ID.eq(flow.id().get())
                : DSL.trueCondition();

        return dsl.selectFrom(PHYSICAL_FLOW)
                .where(PHYSICAL_FLOW.LOGICAL_FLOW_ID.eq(flow.logicalFlowId()))
                .and(PHYSICAL_FLOW.SPECIFICATION_ID.eq(flow.specificationId()))
                .and(PHYSICAL_FLOW.BASIS_OFFSET.eq(flow.basisOffset()))
                .and(PHYSICAL_FLOW.FREQUENCY.eq(flow.frequency().name()))
                .and(PHYSICAL_FLOW.TRANSPORT.eq(flow.transport()))
                .and(PHYSICAL_FLOW.CRITICALITY.eq(flow.criticality().name()))
                .and(idCondition)
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    public int delete(long flowId) {
        return dsl.delete(PHYSICAL_FLOW)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .execute();
    }


    private List<PhysicalFlow> findByCondition(Condition condition) {
        return dsl
                .select(PHYSICAL_FLOW.fields())
                .from(PHYSICAL_FLOW)
                .where(condition)
                .fetch(TO_DOMAIN_MAPPER);
    }


    public long create(PhysicalFlow flow) {
        checkNotNull(flow, "flow cannot be null");
        checkFalse(flow.id().isPresent(), "flow must not have an id");

        PhysicalFlowRecord record = dsl.newRecord(PHYSICAL_FLOW);
        record.setLogicalFlowId(flow.logicalFlowId());

        record.setFrequency(flow.frequency().name());
        record.setTransport(flow.transport());
        record.setBasisOffset(flow.basisOffset());
        record.setCriticality(flow.criticality().name());

        record.setSpecificationId(flow.specificationId());

        record.setDescription(flow.description());
        record.setLastUpdatedBy(flow.lastUpdatedBy());
        record.setLastUpdatedAt(Timestamp.valueOf(flow.lastUpdatedAt()));
        record.setLastAttestedBy(flow.lastAttestedBy().orElse(null));
        record.setLastAttestedAt(flow.lastAttestedAt().map(Timestamp::valueOf).orElse(null));
        record.setIsRemoved(flow.isRemoved());
        record.setProvenance("waltz");
        record.setExternalId(flow.externalId().orElse(null));

        record.setCreatedAt(flow.created().map(UserTimestamp::atTimestamp).orElse(Timestamp.valueOf(flow.lastUpdatedAt())));
        record.setCreatedBy(flow.created().map(UserTimestamp::by).orElse(flow.lastUpdatedBy()));

        record.store();
        return record.getId();
    }


    public int updateSpecDefinition(String userName, long flowId, long newSpecDefinitionId) {
        checkNotNull(userName, "userName cannot be null");

        return dsl.update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.SPECIFICATION_DEFINITION_ID, newSpecDefinitionId)
                .set(PHYSICAL_FLOW.LAST_UPDATED_BY, userName)
                .set(PHYSICAL_FLOW.LAST_UPDATED_AT, nowUtcTimestamp())
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .and(PHYSICAL_FLOW.SPECIFICATION_DEFINITION_ID.isNull()
                        .or(PHYSICAL_FLOW.SPECIFICATION_DEFINITION_ID.ne(newSpecDefinitionId)))
                .execute();
    }


    public int cleanupOrphans() {
        Select<Record1<Long>> allLogicalFlowIds = DSL.select(LOGICAL_FLOW.ID)
                .from(LOGICAL_FLOW)
                .where(LOGICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name()));

        Select<Record1<Long>> allPhysicalSpecs = DSL.select(PHYSICAL_SPECIFICATION.ID)
                .from(PHYSICAL_SPECIFICATION)
                .where(PHYSICAL_SPECIFICATION.IS_REMOVED.eq(false));

        Condition missingLogical = PHYSICAL_FLOW.LOGICAL_FLOW_ID.notIn(allLogicalFlowIds);
        Condition missingSpec = PHYSICAL_FLOW.SPECIFICATION_ID.notIn(allPhysicalSpecs);
        Condition notRemoved = PHYSICAL_FLOW.IS_REMOVED.eq(false);

        Condition requiringCleanup = notRemoved.and(missingLogical.or(missingSpec));

        List<Long> ids = dsl.select(PHYSICAL_FLOW.ID)
                .from(PHYSICAL_FLOW)
                .where(requiringCleanup)
                .fetch(PHYSICAL_FLOW.ID);

        LOG.info("Physical flow cleanupOrphans. The following flows will be marked as removed as one or both endpoints no longer exist: {}", ids);

        return dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.IS_REMOVED, true)
                .where(requiringCleanup)
                .execute();
    }


    private Select<Record> findByProducerEntityReferenceQuery(EntityReference producer) {
        Condition isSender = LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(producer.id())
                .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(producer.kind().name()))
                .and(LOGICAL_NOT_REMOVED);

        return dsl
                .selectDistinct(PHYSICAL_FLOW.fields())
                .from(PHYSICAL_FLOW)
                .innerJoin(PHYSICAL_SPECIFICATION)
                .on(PHYSICAL_SPECIFICATION.ID.eq(PHYSICAL_FLOW.SPECIFICATION_ID))
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .where(dsl.renderInlined(isSender))
                .and(PHYSICAL_FLOW_NOT_REMOVED);
    }


    private Select<Record> findByConsumerEntityReferenceQuery(EntityReference consumer) {
        Condition matchesLogicalFlow = LOGICAL_FLOW.TARGET_ENTITY_ID.eq(consumer.id())
                .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(consumer.kind().name()))
                .and(LOGICAL_NOT_REMOVED);

        return dsl
                .selectDistinct(PHYSICAL_FLOW.fields())
                .from(PHYSICAL_FLOW)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .where(dsl.renderInlined(matchesLogicalFlow))
                .and(PHYSICAL_FLOW_NOT_REMOVED);
    }


    private Select<Record> findByProducerAndConsumerEntityReferenceQuery(EntityReference producer, EntityReference consumer) {
        Condition matchesLogicalFlow = LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(producer.id())
                .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(consumer.kind().name()))
                .and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(consumer.id()))
                .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(consumer.kind().name()))
                .and(LOGICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name()));

        return dsl
                .select(PHYSICAL_FLOW.fields())
                .from(PHYSICAL_FLOW)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.ID.eq(PHYSICAL_FLOW.LOGICAL_FLOW_ID))
                .where(dsl.renderInlined(matchesLogicalFlow));
    }


    public int updateCriticality(long flowId, Criticality criticality) {
        return updateEnum(flowId, PHYSICAL_FLOW.CRITICALITY, criticality.name());
    }


    public int updateFrequency(long flowId, FrequencyKind frequencyKind) {
        return updateEnum(flowId, PHYSICAL_FLOW.FREQUENCY, frequencyKind.name());
    }


    public int updateExternalId(long flowId, String externalId) {
        return dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.EXTERNAL_ID, externalId)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .execute();
    }


    public int updateTransport(long flowId, String transport) {
        Condition enumValueExists = EnumValueDao.mkExistsCondition(EnumValueKind.TRANSPORT_KIND, transport);
        return dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.TRANSPORT, transport)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .and(enumValueExists)
                .execute();
    }


    public int updateBasisOffset(long flowId, int basis) {
        return dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.BASIS_OFFSET, basis)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .execute();
    }


    public int updateDescription(long flowId, String description) {
        return dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.DESCRIPTION, description)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .execute();
    }


    public int updateEntityLifecycleStatus(long flowId, EntityLifecycleStatus entityLifecycleStatus) {
        return dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.ENTITY_LIFECYCLE_STATUS, entityLifecycleStatus.name())
                .set(PHYSICAL_FLOW.IS_REMOVED, entityLifecycleStatus == EntityLifecycleStatus.REMOVED)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .execute();
    }


    public boolean hasPhysicalFlows(long logicalFlowId) {
        return dsl.fetchCount(DSL.selectFrom(PHYSICAL_FLOW)
                .where(PHYSICAL_FLOW.LOGICAL_FLOW_ID.eq(logicalFlowId))
                .and(PHYSICAL_FLOW.IS_REMOVED.eq(false))) > 0;
    }


    // --- helpers

    private int updateEnum(long flowId, TableField<PhysicalFlowRecord, String> field, String value) {
        return dsl
                .update(PHYSICAL_FLOW)
                .set(field, value)
                .where(PHYSICAL_FLOW.ID.eq(flowId))
                .execute();
    }
}
