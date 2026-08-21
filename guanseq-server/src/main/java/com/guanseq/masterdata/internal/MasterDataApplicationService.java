package com.guanseq.masterdata.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.masterdata.api.CustomerRecord;
import com.guanseq.masterdata.api.MasterDataBatchRequest;
import com.guanseq.masterdata.api.MaterialRecord;
import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.masterdata.api.PageResult;

@Service
public class MasterDataApplicationService implements MasterDataReferenceProvider {

	private final CurrentWorkspaceProvider currentWorkspaceProvider;
	private final CustomerRepository customerRepository;
	private final MaterialRepository materialRepository;
	private final MasterDataChangeEventRepository changeEventRepository;

	MasterDataApplicationService(
			CurrentWorkspaceProvider currentWorkspaceProvider,
			CustomerRepository customerRepository,
			MaterialRepository materialRepository,
			MasterDataChangeEventRepository changeEventRepository) {
		this.currentWorkspaceProvider = currentWorkspaceProvider;
		this.customerRepository = customerRepository;
		this.materialRepository = materialRepository;
		this.changeEventRepository = changeEventRepository;
	}

	@Transactional(readOnly = true)
	public PageResult<CustomerRecord> listCustomers(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		Page<CustomerEntity> result = customerRepository.search(
				access.tenantOrganizationId(),
				normalizeQuery(query),
				normalizeStatus(status),
				pageable(page, size));
		return pageResult(result, this::toCustomerRecord);
	}

	@Transactional
	public CustomerRecord createCustomer(String username, CustomerRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		CustomerEntity entity = new CustomerEntity(
				access.tenantOrganizationId(),
				access.operatingOrganizationId(),
				access.userId());
		entity.update(request.code(), request.name(), request.customerType(), request.creditLevel(), request.contactName(), request.contactPhone(), request.owner(), access.userId());
		try {
			customerRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw duplicateCode("客户编码已存在", exception);
		}
		audit(access, "CUSTOMER", entity.getId(), "CREATED", Map.of("code", entity.getCode()));
		return toCustomerRecord(entity);
	}

	@Transactional
	public CustomerRecord updateCustomer(String username, UUID id, CustomerRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		CustomerEntity entity = requireCustomer(access, id);
		requireVersion(entity.getVersion(), request.expectedVersion());
		entity.update(request.code(), request.name(), request.customerType(), request.creditLevel(), request.contactName(), request.contactPhone(), request.owner(), access.userId());
		try {
			customerRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw duplicateCode("客户编码已存在", exception);
		}
		audit(access, "CUSTOMER", entity.getId(), "UPDATED", Map.of("code", entity.getCode()));
		return toCustomerRecord(entity);
	}

	@Transactional
	public List<CustomerRecord> batchCustomers(String username, MasterDataBatchRequest request) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		requireBatchChange(request);
		return request.records().stream().map(target -> {
			CustomerEntity entity = requireCustomer(access, target.id());
			requireVersion(entity.getVersion(), target.expectedVersion());
			if (request.status() != null) entity.changeStatus(request.status(), access.userId());
			if (request.owner() != null && !request.owner().isBlank()) {
				entity.update(entity.getCode(), entity.getName(), entity.getCustomerType(), entity.getCreditLevel(), entity.getContactName(), entity.getContactPhone(), request.owner(), access.userId());
			}
			customerRepository.saveAndFlush(entity);
			audit(access, "CUSTOMER", entity.getId(), request.status() == null ? "BATCH_UPDATED" : request.status().equals("ACTIVE") ? "RESTORED" : "DEACTIVATED", Map.of("code", entity.getCode()));
			return toCustomerRecord(entity);
		}).toList();
	}

	@Transactional(readOnly = true)
	public PageResult<MaterialRecord> listMaterials(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		Page<MaterialEntity> result = materialRepository.search(
				access.tenantOrganizationId(),
				normalizeQuery(query),
				normalizeStatus(status),
				pageable(page, size));
		return pageResult(result, this::toMaterialRecord);
	}

	@Transactional
	public MaterialRecord createMaterial(String username, MaterialRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		MaterialEntity entity = new MaterialEntity(
				access.tenantOrganizationId(),
				access.operatingOrganizationId(),
				access.userId());
		entity.update(request.code(), request.name(), request.specification(), request.materialType(), request.baseUnit(), request.procurementType(), request.owner(), access.userId());
		try {
			materialRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw duplicateCode("物料编码已存在", exception);
		}
		audit(access, "MATERIAL", entity.getId(), "CREATED", Map.of("code", entity.getCode()));
		return toMaterialRecord(entity);
	}

	@Transactional
	public MaterialRecord updateMaterial(String username, UUID id, MaterialRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		MaterialEntity entity = requireMaterial(access, id);
		requireVersion(entity.getVersion(), request.expectedVersion());
		entity.update(request.code(), request.name(), request.specification(), request.materialType(), request.baseUnit(), request.procurementType(), request.owner(), access.userId());
		try {
			materialRepository.saveAndFlush(entity);
		} catch (DataIntegrityViolationException exception) {
			throw duplicateCode("物料编码已存在", exception);
		}
		audit(access, "MATERIAL", entity.getId(), "UPDATED", Map.of("code", entity.getCode()));
		return toMaterialRecord(entity);
	}

	@Transactional
	public List<MaterialRecord> batchMaterials(String username, MasterDataBatchRequest request) {
		CurrentWorkspaceAccess access = currentWorkspaceProvider.resolve(username);
		requireBatchChange(request);
		return request.records().stream().map(target -> {
			MaterialEntity entity = requireMaterial(access, target.id());
			requireVersion(entity.getVersion(), target.expectedVersion());
			if (request.status() != null) entity.changeStatus(request.status(), access.userId());
			if (request.owner() != null && !request.owner().isBlank()) {
				entity.update(entity.getCode(), entity.getName(), entity.getSpecification(), entity.getMaterialType(), entity.getBaseUnit(), entity.getProcurementType(), request.owner(), access.userId());
			}
			materialRepository.saveAndFlush(entity);
			audit(access, "MATERIAL", entity.getId(), request.status() == null ? "BATCH_UPDATED" : request.status().equals("ACTIVE") ? "RESTORED" : "DEACTIVATED", Map.of("code", entity.getCode()));
			return toMaterialRecord(entity);
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CustomerReference requireActiveCustomer(UUID tenantOrganizationId, UUID customerId) {
		CustomerEntity entity = customerRepository.findByIdAndTenantOrganizationIdAndStatus(customerId, tenantOrganizationId, "ACTIVE")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "客户不存在、已停用或不在当前租户范围"));
		return new CustomerReference(entity.getId(), entity.getCode(), entity.getName(), entity.getCreditLevel());
	}

	@Override
	@Transactional(readOnly = true)
	public MaterialReference requireActiveMaterial(UUID tenantOrganizationId, UUID materialId) {
		MaterialEntity entity = materialRepository.findByIdAndTenantOrganizationIdAndStatus(materialId, tenantOrganizationId, "ACTIVE")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "物料不存在、已停用或不在当前租户范围"));
		return new MaterialReference(entity.getId(), entity.getCode(), entity.getName(), entity.getSpecification(), entity.getBaseUnit(), entity.getProcurementType(), entity.isIncomingInspectionRequired());
	}

	@Override
	@Transactional(readOnly = true)
	public List<CustomerReference> listActiveCustomers(UUID tenantOrganizationId) {
		return customerRepository.findAllByTenantOrganizationIdAndStatusOrderByCode(tenantOrganizationId, "ACTIVE").stream()
				.map(entity -> new CustomerReference(entity.getId(), entity.getCode(), entity.getName(), entity.getCreditLevel()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<MaterialReference> listActiveMaterials(UUID tenantOrganizationId) {
		return materialRepository.findAllByTenantOrganizationIdAndStatusOrderByCode(tenantOrganizationId, "ACTIVE").stream()
				.map(entity -> new MaterialReference(entity.getId(), entity.getCode(), entity.getName(), entity.getSpecification(), entity.getBaseUnit(), entity.getProcurementType(), entity.isIncomingInspectionRequired()))
				.toList();
	}

	private CustomerEntity requireCustomer(CurrentWorkspaceAccess access, UUID id) {
		return customerRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "客户不存在或不在当前组织范围"));
	}

	private MaterialEntity requireMaterial(CurrentWorkspaceAccess access, UUID id) {
		return materialRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "物料不存在或不在当前组织范围"));
	}

	private void audit(CurrentWorkspaceAccess access, String entityType, UUID entityId, String action, Map<String, Object> details) {
		changeEventRepository.save(new MasterDataChangeEventEntity(
				access.tenantOrganizationId(), access.workspaceId(), access.userId(), entityType, entityId,
				action, MDC.get("requestId"), details));
	}

	private static void requireVersion(long current, long expected) {
		if (current != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "记录已经被其他用户修改，请刷新后重试");
	}

	private static void requireBatchChange(MasterDataBatchRequest request) {
		if (request.status() == null && (request.owner() == null || request.owner().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量操作至少需要修改状态或负责人");
		}
	}

	private static String normalizeQuery(String query) {
		return query == null || query.isBlank() ? "" : query.trim();
	}

	private static String normalizeStatus(String status) {
		return status == null || status.isBlank() || status.equals("ALL") ? null : status;
	}

	private static PageRequest pageable(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by(Sort.Direction.ASC, "code"));
	}

	private static <E, R> PageResult<R> pageResult(Page<E> page, Function<E, R> mapper) {
		return new PageResult<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
	}

	private CustomerRecord toCustomerRecord(CustomerEntity entity) {
		return new CustomerRecord(entity.getId(), entity.getCode(), entity.getName(), entity.getCustomerType(), entity.getCreditLevel(), entity.getContactName(), entity.getContactPhone(), entity.getOwner(), entity.getStatus(), entity.getVersion(), entity.getUpdatedAt());
	}

	private MaterialRecord toMaterialRecord(MaterialEntity entity) {
		return new MaterialRecord(entity.getId(), entity.getCode(), entity.getName(), entity.getSpecification(), entity.getMaterialType(), entity.getBaseUnit(), entity.getProcurementType(), entity.isIncomingInspectionRequired(), entity.getOwner(), entity.getStatus(), entity.getVersion(), entity.getUpdatedAt());
	}

	private static ResponseStatusException duplicateCode(String message, DataIntegrityViolationException cause) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
	}
}

