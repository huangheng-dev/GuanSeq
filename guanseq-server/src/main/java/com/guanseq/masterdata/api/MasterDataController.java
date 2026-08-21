package com.guanseq.masterdata.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.masterdata.internal.MasterDataApplicationService;

@RestController
@RequestMapping(path = "/api/v1/masterdata", produces = MediaType.APPLICATION_JSON_VALUE)
public class MasterDataController {

	private final MasterDataApplicationService service;

	MasterDataController(MasterDataApplicationService service) {
		this.service = service;
	}

	@GetMapping("/customers")
	PageResult<CustomerRecord> listCustomers(Principal principal, @RequestParam(required = false) String query, @RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.listCustomers(principal.getName(), query, status, page, size);
	}

	@PostMapping(path = "/customers", consumes = MediaType.APPLICATION_JSON_VALUE)
	CustomerRecord createCustomer(Principal principal, @Valid @RequestBody CustomerRecord.CreateRequest request) {
		return service.createCustomer(principal.getName(), request);
	}

	@PutMapping(path = "/customers/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	CustomerRecord updateCustomer(Principal principal, @PathVariable UUID id, @Valid @RequestBody CustomerRecord.UpdateRequest request) {
		return service.updateCustomer(principal.getName(), id, request);
	}

	@PatchMapping(path = "/customers/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
	List<CustomerRecord> batchCustomers(Principal principal, @Valid @RequestBody MasterDataBatchRequest request) {
		return service.batchCustomers(principal.getName(), request);
	}

	@GetMapping("/materials")
	PageResult<MaterialRecord> listMaterials(Principal principal, @RequestParam(required = false) String query, @RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.listMaterials(principal.getName(), query, status, page, size);
	}

	@PostMapping(path = "/materials", consumes = MediaType.APPLICATION_JSON_VALUE)
	MaterialRecord createMaterial(Principal principal, @Valid @RequestBody MaterialRecord.CreateRequest request) {
		return service.createMaterial(principal.getName(), request);
	}

	@PutMapping(path = "/materials/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	MaterialRecord updateMaterial(Principal principal, @PathVariable UUID id, @Valid @RequestBody MaterialRecord.UpdateRequest request) {
		return service.updateMaterial(principal.getName(), id, request);
	}

	@PatchMapping(path = "/materials/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
	List<MaterialRecord> batchMaterials(Principal principal, @Valid @RequestBody MasterDataBatchRequest request) {
		return service.batchMaterials(principal.getName(), request);
	}
}
