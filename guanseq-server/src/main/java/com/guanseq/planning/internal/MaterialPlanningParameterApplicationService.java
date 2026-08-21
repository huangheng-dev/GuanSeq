package com.guanseq.planning.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider.MaterialReference;
import com.guanseq.planning.api.MaterialPlanningParameterPage;
import com.guanseq.planning.api.MaterialPlanningParameterRecord;

@Service
public class MaterialPlanningParameterApplicationService {
	private static final Set<String> PLANNING_ROLES = Set.of("PLANNING_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final MasterDataReferenceProvider masterDataProvider;
	private final MaterialPlanningParameterRepository repository;

	MaterialPlanningParameterApplicationService(CurrentWorkspaceProvider workspaceProvider,
			MasterDataReferenceProvider masterDataProvider, MaterialPlanningParameterRepository repository) {
		this.workspaceProvider = workspaceProvider; this.masterDataProvider = masterDataProvider; this.repository = repository;
	}

	@Transactional(readOnly = true)
	public MaterialPlanningParameterPage list(String username, String query, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalized = query == null ? "" : query.trim().toLowerCase();
		List<MaterialReference> all = masterDataProvider.listActiveMaterials(access.tenantOrganizationId()).stream()
				.filter(item -> normalized.isEmpty() || (item.code() + item.name()).toLowerCase().contains(normalized))
				.sorted(Comparator.comparing(MaterialReference::code)).toList();
		Map<UUID, MaterialPlanningParameterEntity> configured = repository.findByTenantOrganizationIdAndMaterialIdIn(
				access.tenantOrganizationId(), all.stream().map(MaterialReference::id).toList()).stream()
				.collect(Collectors.toMap(MaterialPlanningParameterEntity::getMaterialId, Function.identity()));
		int actualSize = Math.min(100, Math.max(1, size)); int actualPage = Math.max(0, page);
		int start = Math.min(all.size(), actualPage * actualSize); int end = Math.min(all.size(), start + actualSize);
		List<MaterialPlanningParameterRecord> items = all.subList(start, end).stream()
				.map(material -> toRecord(material, configured.get(material.id()))).toList();
		int totalPages = all.isEmpty() ? 0 : (all.size() + actualSize - 1) / actualSize;
		return new MaterialPlanningParameterPage(items, all.size(), actualPage, actualSize, totalPages);
	}

	@Transactional
	public MaterialPlanningParameterRecord update(String username, UUID materialId, MaterialPlanningParameterRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access);
		MaterialReference material = masterDataProvider.requireActiveMaterial(access.tenantOrganizationId(), materialId);
		MaterialPlanningParameterEntity entity = repository.findByMaterialIdAndTenantOrganizationId(materialId, access.tenantOrganizationId()).orElse(null);
		if (entity == null) {
			if (request.expectedVersion() != 0) throw conflict();
			entity = new MaterialPlanningParameterEntity(access.tenantOrganizationId(), access.operatingOrganizationId(), material, request.leadTimeDays(), access.userId());
		} else {
			if (entity.getVersion() != request.expectedVersion()) throw conflict();
			entity.update(request.leadTimeDays(), access.userId());
		}
		return toRecord(material, repository.saveAndFlush(entity));
	}

	private static MaterialPlanningParameterRecord toRecord(MaterialReference material, MaterialPlanningParameterEntity entity) {
		return new MaterialPlanningParameterRecord(material.id(), material.code(), material.name(), material.specification(),
				material.procurementType(), material.baseUnit(), entity == null ? null : entity.getLeadTimeDays(), entity != null,
				entity == null ? 0 : entity.getVersion(), entity == null ? null : entity.getUpdatedAt());
	}
	private static void requireRole(CurrentWorkspaceAccess access) { if (!PLANNING_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护物料计划参数"); }
	private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "计划参数已经被其他用户修改，请刷新后重试"); }
}
