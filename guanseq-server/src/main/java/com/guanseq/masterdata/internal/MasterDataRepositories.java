package com.guanseq.masterdata.internal;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

	@Query("""
			select c from CustomerEntity c
			where c.tenantOrganizationId = :tenantId
			and (:status is null or c.status = :status)
			and (:query = ''
				or lower(c.code) like lower(concat('%', :query, '%'))
				or lower(c.name) like lower(concat('%', :query, '%'))
				or lower(coalesce(c.contactName, '')) like lower(concat('%', :query, '%')))
			""")
	Page<CustomerEntity> search(
			@Param("tenantId") UUID tenantId,
			@Param("query") String query,
			@Param("status") String status,
			Pageable pageable);

	Optional<CustomerEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	Optional<CustomerEntity> findByIdAndTenantOrganizationIdAndStatus(UUID id, UUID tenantOrganizationId, String status);

	List<CustomerEntity> findAllByTenantOrganizationIdAndStatusOrderByCode(UUID tenantOrganizationId, String status);
}

interface MaterialRepository extends JpaRepository<MaterialEntity, UUID> {

	@Query("""
			select m from MaterialEntity m
			where m.tenantOrganizationId = :tenantId
			and (:status is null or m.status = :status)
			and (:query = ''
				or lower(m.code) like lower(concat('%', :query, '%'))
				or lower(m.name) like lower(concat('%', :query, '%'))
				or lower(coalesce(m.specification, '')) like lower(concat('%', :query, '%')))
			""")
	Page<MaterialEntity> search(
			@Param("tenantId") UUID tenantId,
			@Param("query") String query,
			@Param("status") String status,
			Pageable pageable);

	Optional<MaterialEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	Optional<MaterialEntity> findByIdAndTenantOrganizationIdAndStatus(UUID id, UUID tenantOrganizationId, String status);

	List<MaterialEntity> findAllByTenantOrganizationIdAndStatusOrderByCode(UUID tenantOrganizationId, String status);
}

interface MasterDataChangeEventRepository extends JpaRepository<MasterDataChangeEventEntity, UUID> {
}
