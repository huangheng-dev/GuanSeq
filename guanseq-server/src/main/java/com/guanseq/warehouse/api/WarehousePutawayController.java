package com.guanseq.warehouse.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.warehouse.internal.PutawayApplicationService;

@RestController
@RequestMapping(path="/api/v1/warehouse", produces=MediaType.APPLICATION_JSON_VALUE)
public class WarehousePutawayController {
	private final PutawayApplicationService service;
	WarehousePutawayController(PutawayApplicationService service){this.service=service;}
	@GetMapping("/putaway-reference-data")
	WarehousePutawayReferenceData referenceData(Principal principal){return service.referenceData(principal.getName());}
	@GetMapping("/putaway-tasks")
	WarehousePutawayPage list(Principal principal, @RequestParam(required=false) String query,
			@RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="0") int page,
			@RequestParam(defaultValue="50") int size){return service.list(principal.getName(),query,status,page,size);}
	@PostMapping(path="/putaway-tasks", consumes=MediaType.APPLICATION_JSON_VALUE)
	WarehousePutawayRecord create(Principal principal,@Valid @RequestBody WarehousePutawayRecord.CreateRequest request){return service.create(principal.getName(),request);}
	@PostMapping(path="/putaway-tasks/{id}/complete", consumes=MediaType.APPLICATION_JSON_VALUE)
	WarehousePutawayRecord complete(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehousePutawayRecord.CompleteRequest request){return service.complete(principal.getName(),id,request);}
	@PostMapping(path="/putaway-tasks/{id}/cancel", consumes=MediaType.APPLICATION_JSON_VALUE)
	WarehousePutawayRecord cancel(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehousePutawayRecord.CancelRequest request){return service.cancel(principal.getName(),id,request);}
	@PostMapping(path="/putaway-tasks/{id}/reverse", consumes=MediaType.APPLICATION_JSON_VALUE)
	WarehousePutawayRecord reverse(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehousePutawayRecord.ReverseRequest request){return service.reverse(principal.getName(),id,request);}
}
