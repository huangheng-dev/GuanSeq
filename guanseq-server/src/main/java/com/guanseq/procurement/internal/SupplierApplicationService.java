package com.guanseq.procurement.internal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.procurement.api.SupplierPage;
import com.guanseq.procurement.api.SupplierRecord;

@org.springframework.stereotype.Service
public class SupplierApplicationService {
	private static final Set<String> WRITE_ROLES = Set.of("PROCUREMENT_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final SupplierRepository supplierRepository;
	private final SupplierEventRepository eventRepository;

	SupplierApplicationService(CurrentWorkspaceProvider workspaceProvider, SupplierRepository supplierRepository,
			SupplierEventRepository eventRepository) {
		this.workspaceProvider = workspaceProvider;
		this.supplierRepository = supplierRepository;
		this.eventRepository = eventRepository;
	}

	@Transactional(readOnly = true)
	public SupplierPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		Page<SupplierEntity> result = supplierRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("code").ascending()));
		return new SupplierPage(result.getContent().stream().map(this::toRecord).toList(),
				result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
	}

	@Transactional
	public SupplierRecord create(String username, SupplierRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access);
		String code = request.code().trim().toUpperCase();
		if (supplierRepository.existsByTenantOrganizationIdAndCode(access.tenantOrganizationId(), code))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "供应商编码已存在");
		SupplierEntity supplier = new SupplierEntity(UUID.randomUUID(), access.tenantOrganizationId(), access.operatingOrganizationId(),
				code, request.name().trim(), trimToNull(request.contactName()), trimToNull(request.contactPhone()), access.userId());
		supplierRepository.save(supplier);
		eventRepository.save(new SupplierEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(), supplier.getId(),
				"CREATED", MDC.get("requestId"), Map.of("supplierCode", code)));
		return toRecord(supplier);
	}

	@Transactional
	public SupplierRecord update(String username, UUID id, SupplierRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access);
		SupplierEntity supplier = supplierRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "供应商不存在"));
		requireVersion(supplier, request.expectedVersion());
		supplier.update(request.name().trim(), trimToNull(request.contactName()), trimToNull(request.contactPhone()), access.userId());
		supplierRepository.save(supplier);
		eventRepository.save(new SupplierEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(), supplier.getId(),
				"UPDATED", MDC.get("requestId"), Map.of("supplierCode", supplier.getCode())));
		return toRecord(supplier);
	}

	@Transactional
	public SupplierRecord changeStatus(String username, UUID id, String status, long expectedVersion) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireWriteRole(access);
		SupplierEntity supplier = supplierRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "供应商不存在"));
		requireVersion(supplier, expectedVersion);
		if (status.equals(supplier.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "供应商状态未变化");
		supplier.toggleStatus(status, access.userId());
		supplierRepository.save(supplier);
		eventRepository.save(new SupplierEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(), supplier.getId(),
				"ACTIVE".equals(status) ? "ENABLED" : "DISABLED", MDC.get("requestId"), Map.of("status", status)));
		return toRecord(supplier);
	}

	private SupplierRecord toRecord(SupplierEntity s) {
		return new SupplierRecord(s.getId(), s.getCode(), s.getName(), s.getContactName(), s.getContactPhone(), s.getStatus(), s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
	}
	private static void requireWriteRole(CurrentWorkspaceAccess access) {
		if (!WRITE_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护供应商");
	}
	private static void requireVersion(SupplierEntity supplier, long expectedVersion) {
		if (supplier.getVersion() != expectedVersion) throw new ResponseStatusException(HttpStatus.CONFLICT, "供应商已经被其他用户修改，请刷新后重试");
	}
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String normalizeStatus(String value) { return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(); }
	private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
