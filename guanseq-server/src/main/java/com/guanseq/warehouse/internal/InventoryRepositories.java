package com.guanseq.warehouse.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WarehouseRepository extends JpaRepository<WarehouseEntity, UUID> {
	List<WarehouseEntity> findByTenantOrganizationIdAndStatusOrderByCodeAsc(UUID tenantId, String status);
	Optional<WarehouseEntity> findByIdAndTenantOrganizationIdAndStatus(UUID id, UUID tenantId, String status);
}

interface StorageLocationRepository extends JpaRepository<StorageLocationEntity, UUID> {
	List<StorageLocationEntity> findByTenantOrganizationIdAndStatusOrderByCodeAsc(UUID tenantId, String status);
	Optional<StorageLocationEntity> findByIdAndTenantOrganizationIdAndStatus(UUID id, UUID tenantId, String status);
}

interface StockBalanceRepository extends JpaRepository<StockBalanceEntity, UUID> {
	@Query("""
			select b from StockBalanceEntity b where b.tenantOrganizationId = :tenantId
			and (:quality = '' or b.qualityStatus = :quality)
			and (:warehouse = '' or b.warehouseCode = :warehouse)
			and (:query = '' or lower(b.materialCode) like lower(concat('%', :query, '%'))
				or lower(b.materialName) like lower(concat('%', :query, '%'))
				or lower(b.warehouseCode) like lower(concat('%', :query, '%'))
				or lower(b.locationCode) like lower(concat('%', :query, '%'))
				or lower(b.lotNumber) like lower(concat('%', :query, '%')))
			""")
	Page<StockBalanceEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("quality") String quality, @Param("warehouse") String warehouse, Pageable pageable);

	Optional<StockBalanceEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<StockBalanceEntity> findByTenantOrganizationIdAndWarehouseIdAndLocationIdAndMaterialIdAndLotNumberAndQualityStatus(
			UUID tenantId, UUID warehouseId, UUID locationId, UUID materialId, String lotNumber, String qualityStatus);
	List<StockBalanceEntity> findByTenantOrganizationIdAndMaterialIdIn(UUID tenantId, Collection<UUID> materialIds);
	List<StockBalanceEntity> findByTenantOrganizationIdAndWarehouseIdAndMaterialIdIn(UUID tenantId, UUID warehouseId,
			Collection<UUID> materialIds);
	List<StockBalanceEntity> findByTenantOrganizationIdAndWarehouseIdAndMaterialIdAndQualityStatusOrderByLocationCodeAscLotNumberAscUpdatedAtAsc(
		UUID tenantId, UUID warehouseId, UUID materialId, String qualityStatus);
}

interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {
	List<StockMovementEntity> findByBalanceIdOrderByOccurredAtDesc(UUID balanceId);
	Optional<StockMovementEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	Optional<StockMovementEntity> findByTenantOrganizationIdAndSourceTypeAndSourceId(UUID tenantId, String sourceType,
			UUID sourceId);
	Optional<StockMovementEntity> findByTenantOrganizationIdAndSourceTypeAndSourceIdAndSourceLineId(UUID tenantId, String sourceType,
			UUID sourceId, UUID sourceLineId);
}

