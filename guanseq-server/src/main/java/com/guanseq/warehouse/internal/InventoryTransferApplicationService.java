package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.warehouse.api.WarehouseInventoryControlReferenceData;
import com.guanseq.warehouse.api.WarehouseTransferPage;
import com.guanseq.warehouse.api.WarehouseTransferRecord;

@Service
public class InventoryTransferApplicationService {
    private static final Set<String> ROLES=Set.of("WAREHOUSE_MANAGER","INVENTORY_CONTROLLER","ADMIN");
    private static final Set<String> STATUSES=Set.of("OPEN","COMPLETED","CANCELLED","REVERSED");
    private final CurrentWorkspaceProvider workspaceProvider;
    private final TransferTaskRepository taskRepository;
    private final TransferEventRepository eventRepository;
    private final StockCountTaskRepository countRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final StorageLocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    InventoryTransferApplicationService(CurrentWorkspaceProvider workspaceProvider,TransferTaskRepository taskRepository,
            TransferEventRepository eventRepository,StockCountTaskRepository countRepository,StockBalanceRepository balanceRepository,
            StockMovementRepository movementRepository,WarehouseRepository warehouseRepository,StorageLocationRepository locationRepository,
            JdbcTemplate jdbcTemplate){
        this.workspaceProvider=workspaceProvider;this.taskRepository=taskRepository;this.eventRepository=eventRepository;
        this.countRepository=countRepository;this.balanceRepository=balanceRepository;this.movementRepository=movementRepository;
        this.warehouseRepository=warehouseRepository;this.locationRepository=locationRepository;this.jdbcTemplate=jdbcTemplate;
    }

    @Transactional(readOnly=true)
    public WarehouseInventoryControlReferenceData referenceData(String username){
        CurrentWorkspaceAccess access=requireRole(username);
        List<StorageLocationEntity> locations=locationRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(access.tenantOrganizationId(),"ACTIVE");
        Map<UUID,StorageLocationEntity> locationById=locations.stream().collect(Collectors.toMap(StorageLocationEntity::getId,Function.identity()));
        Map<UUID,WarehouseEntity> warehouseById=warehouseRepository.findByTenantOrganizationIdAndStatusOrderByCodeAsc(access.tenantOrganizationId(),"ACTIVE")
                .stream().collect(Collectors.toMap(WarehouseEntity::getId,Function.identity()));
        List<WarehouseInventoryControlReferenceData.Balance> balances=balanceRepository
                .findByTenantOrganizationIdAndOnHandQuantityGreaterThan(access.tenantOrganizationId(),BigDecimal.ZERO,
                        PageRequest.of(0,1000,Sort.by("updatedAt").descending())).stream()
                .filter(item->locationById.containsKey(item.getLocationId())&&warehouseById.containsKey(item.getWarehouseId()))
                .map(item->{StorageLocationEntity location=locationById.get(item.getLocationId());return new WarehouseInventoryControlReferenceData.Balance(
                        item.getId(),item.getVersion(),item.getWarehouseId(),item.getWarehouseCode(),item.getWarehouseName(),item.getLocationId(),
                        item.getLocationCode(),item.getLocationName(),location.getLocationType(),item.getMaterialCode(),item.getMaterialName(),
                        item.getMaterialSpecification(),item.getLotNumber(),item.getUnit(),item.getQualityStatus(),item.getOnHandQuantity(),
                        item.getAllocatedQuantity(),item.getFrozenQuantity(),item.availableQuantity(),
                        taskRepository.openQuantity(access.tenantOrganizationId(),item.getId()),
                        countRepository.hasActiveBalance(access.tenantOrganizationId(),item.getId()));}).toList();
        List<WarehouseInventoryControlReferenceData.TargetLocation> targets=locations.stream().filter(item->"STORAGE".equals(item.getLocationType()))
                .filter(item->warehouseById.containsKey(item.getWarehouseId())).map(item->{WarehouseEntity warehouse=warehouseById.get(item.getWarehouseId());
                    return new WarehouseInventoryControlReferenceData.TargetLocation(item.getId(),item.getWarehouseId(),warehouse.getCode(),
                            item.getCode(),item.getName(),"LOC:"+item.getCode());}).toList();
        return new WarehouseInventoryControlReferenceData(balances,targets);
    }

    @Transactional(readOnly=true)
    public WarehouseTransferPage list(String username,String query,String status,int page,int size){
        CurrentWorkspaceAccess access=requireRole(username);var result=taskRepository.search(access.tenantOrganizationId(),access.workspaceId(),
                normalize(query),normalizeStatus(status),PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"createdAt")));
        return new WarehouseTransferPage(result.getContent().stream().map(this::toRecord).toList(),result.getTotalElements(),
                result.getNumber(),result.getSize(),result.getTotalPages());
    }

    @Transactional
    public WarehouseTransferRecord create(String username,WarehouseTransferRecord.CreateRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("transfer-create");
        var duplicate=taskRepository.findByTenantOrganizationIdAndCreateRequestId(access.tenantOrganizationId(),requestId);
        if(duplicate.isPresent())return toRecord(duplicate.get());
        StockBalanceEntity source=lockBalance(access,request.sourceBalanceId(),"源库存不存在或不在当前租户范围");
        requireVersion(source.getVersion(),request.expectedSourceBalanceVersion(),"源库存已经变化，请重新选择");validateSource(access,source);
        if(countRepository.hasActiveBalance(access.tenantOrganizationId(),source.getId()))throw conflict("源库存正在盘点，不能创建调拨");
        StorageLocationEntity target=requireTarget(access,request.targetLocationId(),source);
        BigDecimal reserved=taskRepository.openQuantity(access.tenantOrganizationId(),source.getId());
        if(request.quantity().compareTo(source.availableQuantity().subtract(reserved))>0)throw unprocessable("可调拨数量不足，可能已被其他开放任务占用");
        String reason=validatedReason(request.reason());TransferTaskEntity task=new TransferTaskEntity(access.tenantOrganizationId(),access.workspaceId(),
                nextTaskNumber(),source,target,request.quantity(),reason,access.userId(),access.username(),requestId);
        try{taskRepository.saveAndFlush(task);event(access,task,"CREATE",null,"OPEN",reason,requestId);}
        catch(DataIntegrityViolationException exception){throw conflict("调拨任务创建发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseTransferRecord complete(String username,UUID id,WarehouseTransferRecord.CompleteRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("transfer-complete");
        WarehouseTransferRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        TransferTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"调拨任务已经变化，请刷新后重试");
        requireStatus(task,"OPEN","只有开放任务可以完成调拨");
        StorageLocationEntity target=locationRepository.findByIdAndTenantOrganizationIdAndStatus(task.getTargetLocationId(),access.tenantOrganizationId(),"ACTIVE")
                .orElseThrow(()->unprocessable("目标库位已停用或不存在"));
        var targetCandidate=balanceRepository.findByTenantOrganizationIdAndWarehouseIdAndLocationIdAndMaterialIdAndLotNumberAndQualityStatus(
                access.tenantOrganizationId(),task.getSourceWarehouseId(),target.getId(),task.getMaterialId(),task.getLotNumber(),"AVAILABLE");
        Map<UUID,StockBalanceEntity> locked=lockBalances(access,task.getSourceBalanceId(),targetCandidate.map(StockBalanceEntity::getId).orElse(null));
        StockBalanceEntity source=locked.get(task.getSourceBalanceId());requireVersion(source.getVersion(),request.expectedSourceBalanceVersion(),"源库存已经变化，请刷新后重试");
        validateSource(access,source);if(countRepository.hasActiveBalance(access.tenantOrganizationId(),source.getId()))throw conflict("源库存正在盘点，不能完成调拨");
        if(!"STORAGE".equals(target.getLocationType())||!target.getWarehouseId().equals(source.getWarehouseId())||target.getId().equals(source.getLocationId()))
            throw unprocessable("目标库位已不再符合同仓调拨条件");
        BigDecimal reservedOther=taskRepository.openQuantity(access.tenantOrganizationId(),source.getId()).subtract(task.getQuantity());
        if(source.availableQuantity().subtract(reservedOther.max(BigDecimal.ZERO)).compareTo(task.getQuantity())<0)throw unprocessable("源库存可用量不足，不能完成调拨");
        WarehouseEntity warehouse=warehouseRepository.findByIdAndTenantOrganizationIdAndStatus(source.getWarehouseId(),access.tenantOrganizationId(),"ACTIVE")
                .orElseThrow(()->unprocessable("源仓库已停用或不存在"));
        StockBalanceEntity targetBalance=targetCandidate.map(item->locked.get(item.getId())).orElseGet(()->new StockBalanceEntity(
                access.tenantOrganizationId(),access.operatingOrganizationId(),access.workspaceId(),warehouse,target,source.getMaterialId(),
                source.getMaterialCode(),source.getMaterialName(),source.getMaterialSpecification(),source.getUnit(),source.getLotNumber(),"AVAILABLE",access.userId()));
        if(targetCandidate.isPresent()&&countRepository.hasActiveBalance(access.tenantOrganizationId(),targetBalance.getId()))throw conflict("目标库存正在盘点，不能完成调拨");
        try{
            StockBalanceEntity.Change outChange=source.apply("ISSUE",task.getQuantity(),access.userId());balanceRepository.saveAndFlush(source);
            StockMovementEntity out=movement(access,source,"ISSUE",task.getQuantity(),"调拨出库 · "+task.getTaskNumber(),requestId+"-OUT",outChange,task,"OUT");
            StockBalanceEntity.Change inChange=targetBalance.apply("RECEIPT",task.getQuantity(),access.userId());balanceRepository.saveAndFlush(targetBalance);
            StockMovementEntity in=movement(access,targetBalance,"RECEIPT",task.getQuantity(),"调拨入库 · "+task.getTaskNumber(),requestId+"-IN",inChange,task,"IN");
            task.complete(targetBalance.getId(),out,in,access.userId(),access.username());taskRepository.saveAndFlush(task);
            event(access,task,"COMPLETE","OPEN","COMPLETED",null,requestId);
        }catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("调拨过账发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseTransferRecord cancel(String username,UUID id,WarehouseTransferRecord.CancelRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("transfer-cancel");
        WarehouseTransferRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        TransferTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"调拨任务已经变化，请刷新后重试");
        requireStatus(task,"OPEN","只有开放任务可以取消");String reason=validatedReason(request.reason());task.cancel(access.userId(),access.username(),reason);
        try{taskRepository.saveAndFlush(task);event(access,task,"CANCEL","OPEN","CANCELLED",reason,requestId);}
        catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("取消调拨发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseTransferRecord reverse(String username,UUID id,WarehouseTransferRecord.ReverseRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("transfer-reverse");
        WarehouseTransferRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        TransferTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"调拨任务已经变化，请刷新后重试");
        requireStatus(task,"COMPLETED","只有已完成调拨可以冲回");
        Map<UUID,StockBalanceEntity> locked=lockBalances(access,task.getSourceBalanceId(),task.getTargetBalanceId());
        StockBalanceEntity source=locked.get(task.getSourceBalanceId());StockBalanceEntity target=locked.get(task.getTargetBalanceId());
        requireVersion(target.getVersion(),request.expectedTargetBalanceVersion(),"目标库存已经变化，请刷新后重试");
        if(countRepository.hasActiveBalance(access.tenantOrganizationId(),source.getId())||countRepository.hasActiveBalance(access.tenantOrganizationId(),target.getId()))
            throw conflict("相关库存正在盘点，不能冲回调拨");
        if(target.availableQuantity().compareTo(task.getQuantity())<0)throw unprocessable("目标库存已被使用，不能冲回该调拨");
        String reason=validatedReason(request.reason());
        try{
            StockBalanceEntity.Change outChange=target.apply("ISSUE",task.getQuantity(),access.userId());balanceRepository.saveAndFlush(target);
            StockMovementEntity out=movement(access,target,"ISSUE",task.getQuantity(),"调拨冲回 · "+reason,requestId+"-OUT",outChange,task,"REVERSE-OUT");
            StockBalanceEntity.Change inChange=source.apply("RECEIPT",task.getQuantity(),access.userId());balanceRepository.saveAndFlush(source);
            StockMovementEntity in=movement(access,source,"RECEIPT",task.getQuantity(),"调拨冲回 · "+reason,requestId+"-IN",inChange,task,"REVERSE-IN");
            task.reverse(out,in,access.userId(),access.username(),reason);taskRepository.saveAndFlush(task);
            event(access,task,"REVERSE","COMPLETED","REVERSED",reason,requestId);
        }catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("调拨冲回发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    private StockMovementEntity movement(CurrentWorkspaceAccess access,StockBalanceEntity balance,String type,BigDecimal quantity,String reason,
            String requestId,StockBalanceEntity.Change change,TransferTaskEntity task,String direction){
        StockMovementEntity movement=new StockMovementEntity(access.tenantOrganizationId(),access.workspaceId(),access.userId(),balance.getId(),
                nextMovementNumber(),type,quantity,reason,requestId,change);
        movement.attachSource("TRANSFER_TASK",task.getId(),task.getTaskNumber(),UUID.nameUUIDFromBytes((task.getId()+":"+direction).getBytes(StandardCharsets.UTF_8)));
        return movementRepository.saveAndFlush(movement);
    }
    private void event(CurrentWorkspaceAccess access,TransferTaskEntity task,String action,String from,String to,String reason,String requestId){
        eventRepository.saveAndFlush(new TransferEventEntity(access.tenantOrganizationId(),access.workspaceId(),task.getId(),action,from,to,reason,
                access.userId(),access.username(),requestId));
    }
    private WarehouseTransferRecord duplicateAction(CurrentWorkspaceAccess access,String requestId,UUID taskId){
        var event=eventRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(),requestId);if(event.isEmpty())return null;
        if(!event.get().getTaskId().equals(taskId))throw conflict("请求编号已用于其他调拨动作");return toRecord(requireTask(access,taskId));
    }
    private TransferTaskEntity requireTask(CurrentWorkspaceAccess access,UUID id){return taskRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(
            id,access.tenantOrganizationId(),access.workspaceId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"调拨任务不存在或不在当前工作区"));}
    private StorageLocationEntity requireTarget(CurrentWorkspaceAccess access,UUID id,StockBalanceEntity source){
        StorageLocationEntity target=locationRepository.findByIdAndTenantOrganizationIdAndStatus(id,access.tenantOrganizationId(),"ACTIVE")
                .orElseThrow(()->unprocessable("目标库位不存在、已停用或不在当前租户范围"));
        if(!"STORAGE".equals(target.getLocationType()))throw unprocessable("目标库位必须是正式存储库位");
        if(!target.getWarehouseId().equals(source.getWarehouseId()))throw unprocessable("库内调拨只能在同一仓库内完成");
        if(target.getId().equals(source.getLocationId()))throw unprocessable("目标库位不能与源库位相同");return target;
    }
    private void validateSource(CurrentWorkspaceAccess access,StockBalanceEntity source){
        if(!"AVAILABLE".equals(source.getQualityStatus()))throw unprocessable("只有合格库存可以调拨");
        StorageLocationEntity location=locationRepository.findByIdAndTenantOrganizationIdAndStatus(source.getLocationId(),access.tenantOrganizationId(),"ACTIVE")
                .orElseThrow(()->unprocessable("源库位已停用或不存在"));
        if(!"STORAGE".equals(location.getLocationType()))throw unprocessable("库内调拨源库存必须位于正式存储库位");
    }
    private Map<UUID,StockBalanceEntity> lockBalances(CurrentWorkspaceAccess access,UUID first,UUID second){
        Map<UUID,StockBalanceEntity> result=new HashMap<>();Stream.of(first,second).filter(java.util.Objects::nonNull).distinct()
                .sorted((left,right)->left.toString().compareTo(right.toString())).forEach(id->result.put(id,lockBalance(access,id,"库存不存在或不在当前租户范围")));
        return result;
    }
    private StockBalanceEntity lockBalance(CurrentWorkspaceAccess access,UUID id,String message){return balanceRepository.findForUpdate(id,access.tenantOrganizationId())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,message));}
    private WarehouseTransferRecord toRecord(TransferTaskEntity task){
        StockBalanceEntity source=balanceRepository.findByIdAndTenantOrganizationId(task.getSourceBalanceId(),task.getTenantOrganizationId()).orElse(null);
        StockBalanceEntity target=task.getTargetBalanceId()==null?null:balanceRepository.findByIdAndTenantOrganizationId(task.getTargetBalanceId(),task.getTenantOrganizationId()).orElse(null);
        return new WarehouseTransferRecord(task.getId(),task.getTaskNumber(),task.getStatus(),task.getVersion(),task.getSourceBalanceId(),
                source==null?-1:source.getVersion(),task.getSourceWarehouseCode(),task.getSourceWarehouseName(),task.getSourceLocationCode(),task.getSourceLocationName(),
                task.getTargetLocationId(),task.getTargetLocationCode(),task.getTargetLocationName(),task.getTargetBalanceId(),target==null?null:target.getVersion(),
                task.getMaterialCode(),task.getMaterialName(),task.getMaterialSpecification(),task.getLotNumber(),task.getUnit(),task.getQualityStatus(),
                task.getQuantity(),task.getTransferReason(),task.getSourceOutMovementId(),task.getSourceOutMovementNumber(),task.getTargetInMovementId(),
                task.getTargetInMovementNumber(),task.getReverseOutMovementId(),task.getReverseOutMovementNumber(),task.getReverseInMovementId(),
                task.getReverseInMovementNumber(),task.getCreatedByUsername(),task.getCreatedAt(),task.getCompletedByUsername(),task.getCompletedAt(),
                task.getCancelledByUsername(),task.getCancelledAt(),task.getCancellationReason(),task.getReversedByUsername(),task.getReversedAt(),
                task.getReversalReason(),task.getCreateRequestId());
    }
    private CurrentWorkspaceAccess requireRole(String username){CurrentWorkspaceAccess access=workspaceProvider.resolve(username);
        if(!ROLES.contains(access.roleCode()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前角色无权执行库内调拨");return access;}
    private String nextTaskNumber(){Long value=jdbcTemplate.queryForObject("select nextval('warehouse.transfer_task_number_seq')",Long.class);
        return "TRF-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",value);}
    private String nextMovementNumber(){Long value=jdbcTemplate.queryForObject("select nextval('warehouse.movement_number_seq')",Long.class);
        return "MOV-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",value);}
    private static String currentRequestId(String prefix){String id=MDC.get("requestId");return id==null||id.isBlank()?prefix+"-"+UUID.randomUUID():id;}
    private static void requireVersion(long current,long expected,String message){if(current!=expected)throw conflict(message);}
    private static void requireStatus(TransferTaskEntity task,String status,String message){if(!status.equals(task.getStatus()))throw conflict(message);}
    private static String validatedReason(String reason){String value=normalize(reason);if(value.length()<4)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"原因至少填写 4 个字符");return value;}
    private static String normalize(String value){return value==null?"":value.trim();}
    private static String normalizeStatus(String value){if(value==null||value.isBlank()||"ALL".equalsIgnoreCase(value))return "";
        String result=value.trim().toUpperCase();if(!STATUSES.contains(result))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"调拨状态筛选无效");return result;}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException unprocessable(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
