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

import com.guanseq.warehouse.internal.InventoryTransferApplicationService;
import com.guanseq.warehouse.internal.StockCountApplicationService;

@RestController
@RequestMapping(path="/api/v1/warehouse",produces=MediaType.APPLICATION_JSON_VALUE)
public class WarehouseInventoryControlController {
    private final InventoryTransferApplicationService transferService;
    private final StockCountApplicationService countService;
    WarehouseInventoryControlController(InventoryTransferApplicationService transferService,StockCountApplicationService countService){
        this.transferService=transferService;this.countService=countService;
    }
    @GetMapping("/inventory-control-reference-data")
    WarehouseInventoryControlReferenceData referenceData(Principal principal){return transferService.referenceData(principal.getName());}
    @GetMapping("/transfer-tasks")
    WarehouseTransferPage transfers(Principal principal,@RequestParam(required=false) String query,@RequestParam(defaultValue="ALL") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return transferService.list(principal.getName(),query,status,page,size);
    }
    @PostMapping(path="/transfer-tasks",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseTransferRecord createTransfer(Principal principal,@Valid @RequestBody WarehouseTransferRecord.CreateRequest request){
        return transferService.create(principal.getName(),request);
    }
    @PostMapping(path="/transfer-tasks/{id}/complete",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseTransferRecord completeTransfer(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseTransferRecord.CompleteRequest request){
        return transferService.complete(principal.getName(),id,request);
    }
    @PostMapping(path="/transfer-tasks/{id}/cancel",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseTransferRecord cancelTransfer(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseTransferRecord.CancelRequest request){
        return transferService.cancel(principal.getName(),id,request);
    }
    @PostMapping(path="/transfer-tasks/{id}/reverse",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseTransferRecord reverseTransfer(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseTransferRecord.ReverseRequest request){
        return transferService.reverse(principal.getName(),id,request);
    }
    @GetMapping("/stock-count-tasks")
    WarehouseStockCountPage counts(Principal principal,@RequestParam(required=false) String query,@RequestParam(defaultValue="ALL") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return countService.list(principal.getName(),query,status,page,size);
    }
    @PostMapping(path="/stock-count-tasks",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseStockCountRecord createCount(Principal principal,@Valid @RequestBody WarehouseStockCountRecord.CreateRequest request){
        return countService.create(principal.getName(),request);
    }
    @PostMapping(path="/stock-count-tasks/{id}/record-count",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseStockCountRecord recordCount(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseStockCountRecord.RecordCountRequest request){
        return countService.recordCount(principal.getName(),id,request);
    }
    @PostMapping(path="/stock-count-tasks/{id}/approve",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseStockCountRecord approveCount(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseStockCountRecord.ApproveRequest request){
        return countService.approve(principal.getName(),id,request);
    }
    @PostMapping(path="/stock-count-tasks/{id}/cancel",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseStockCountRecord cancelCount(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseStockCountRecord.CancelRequest request){
        return countService.cancel(principal.getName(),id,request);
    }
    @PostMapping(path="/stock-count-tasks/{id}/reverse",consumes=MediaType.APPLICATION_JSON_VALUE)
    WarehouseStockCountRecord reverseCount(Principal principal,@PathVariable UUID id,@Valid @RequestBody WarehouseStockCountRecord.ReverseRequest request){
        return countService.reverse(principal.getName(),id,request);
    }
}
