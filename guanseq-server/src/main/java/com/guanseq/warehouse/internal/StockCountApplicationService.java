package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

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
import com.guanseq.warehouse.api.WarehouseStockCountPage;
import com.guanseq.warehouse.api.WarehouseStockCountRecord;

@Service
public class StockCountApplicationService {
    private static final Set<String> ROLES=Set.of("WAREHOUSE_MANAGER","INVENTORY_CONTROLLER","ADMIN");
    private static final Set<String> STATUSES=Set.of("OPEN","COUNTED","APPROVED","CANCELLED","REVERSED");
    private final CurrentWorkspaceProvider workspaceProvider;
    private final StockCountTaskRepository taskRepository;
    private final StockCountEventRepository eventRepository;
    private final TransferTaskRepository transferRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final StorageLocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    StockCountApplicationService(CurrentWorkspaceProvider workspaceProvider,StockCountTaskRepository taskRepository,
            StockCountEventRepository eventRepository,TransferTaskRepository transferRepository,StockBalanceRepository balanceRepository,
            StockMovementRepository movementRepository,WarehouseRepository warehouseRepository,StorageLocationRepository locationRepository,
            JdbcTemplate jdbcTemplate){
        this.workspaceProvider=workspaceProvider;this.taskRepository=taskRepository;this.eventRepository=eventRepository;
        this.transferRepository=transferRepository;this.balanceRepository=balanceRepository;this.movementRepository=movementRepository;
        this.warehouseRepository=warehouseRepository;this.locationRepository=locationRepository;this.jdbcTemplate=jdbcTemplate;
    }

    @Transactional(readOnly=true)
    public WarehouseStockCountPage list(String username,String query,String status,int page,int size){
        CurrentWorkspaceAccess access=requireRole(username);var result=taskRepository.search(access.tenantOrganizationId(),access.workspaceId(),
                normalize(query),normalizeStatus(status),PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"createdAt")));
        return new WarehouseStockCountPage(result.getContent().stream().map(this::toRecord).toList(),result.getTotalElements(),
                result.getNumber(),result.getSize(),result.getTotalPages());
    }

    @Transactional
    public WarehouseStockCountRecord create(String username,WarehouseStockCountRecord.CreateRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("count-create");
        var duplicate=taskRepository.findByTenantOrganizationIdAndCreateRequestId(access.tenantOrganizationId(),requestId);
        if(duplicate.isPresent())return toRecord(duplicate.get());
        StockBalanceEntity balance=lockBalance(access,request.balanceId());requireVersion(balance.getVersion(),request.expectedBalanceVersion(),"库存已经变化，请重新选择");
        validateBalance(access,balance);
        if(taskRepository.hasActiveBalance(access.tenantOrganizationId(),balance.getId()))throw conflict("该库存已经存在开放盘点任务");
        if(transferRepository.hasOpenSource(access.tenantOrganizationId(),balance.getId()))throw conflict("该库存存在开放调拨任务，不能开始盘点");
        StockCountTaskEntity task=new StockCountTaskEntity(access.tenantOrganizationId(),access.workspaceId(),nextCountNumber(),balance,
                access.userId(),access.username(),requestId);
        try{taskRepository.saveAndFlush(task);event(access,task,"CREATE",null,"OPEN",null,requestId);}
        catch(DataIntegrityViolationException exception){throw conflict("盘点任务创建发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseStockCountRecord recordCount(String username,UUID id,WarehouseStockCountRecord.RecordCountRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("count-record");
        WarehouseStockCountRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        StockCountTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"盘点任务已经变化，请刷新后重试");
        requireStatus(task,"OPEN","只有开放盘点可以录入实盘数量");StockBalanceEntity balance=lockBalance(access,task.getBalanceId());
        requireSnapshotVersion(task,balance,request.expectedBalanceVersion());String note=validatedReason(request.note());
        task.recordCount(request.countedQuantity(),note,access.userId(),access.username());
        try{taskRepository.saveAndFlush(task);event(access,task,"RECORD_COUNT","OPEN","COUNTED",note,requestId);}
        catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("实盘录入发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseStockCountRecord approve(String username,UUID id,WarehouseStockCountRecord.ApproveRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("count-approve");
        WarehouseStockCountRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        StockCountTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"盘点任务已经变化，请刷新后重试");
        requireStatus(task,"COUNTED","只有已录入实盘数量的任务可以审批");StockBalanceEntity balance=lockBalance(access,task.getBalanceId());
        requireSnapshotVersion(task,balance,request.expectedBalanceVersion());String comment=validatedReason(request.comment());
        BigDecimal difference=task.getDifferenceQuantity();StockMovementEntity movement=null;
        try{
            if(difference.signum()!=0){
                String type=difference.signum()>0?"RECEIPT":"ISSUE";BigDecimal quantity=difference.abs();
                if("ISSUE".equals(type)&&task.getCountedQuantity().compareTo(balance.getAllocatedQuantity().add(balance.getFrozenQuantity()))<0)
                    throw unprocessable("盘亏后现存量会低于已分配量与冻结量之和，请先处理占用或冻结");
                StockBalanceEntity.Change change=balance.apply(type,quantity,access.userId());balanceRepository.saveAndFlush(balance);
                movement=movement(access,balance,type,quantity,"盘点差异审批 · "+task.getCountNumber()+" · "+comment,requestId+"-ADJUST",change,task,"ADJUST");
            }
            task.approve(movement,comment,access.userId(),access.username());taskRepository.saveAndFlush(task);
            event(access,task,"APPROVE","COUNTED","APPROVED",comment,requestId);
        }catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("盘点差异审批发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseStockCountRecord cancel(String username,UUID id,WarehouseStockCountRecord.CancelRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("count-cancel");
        WarehouseStockCountRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        StockCountTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"盘点任务已经变化，请刷新后重试");
        if(!Set.of("OPEN","COUNTED").contains(task.getStatus()))throw conflict("只有开放或已录入的盘点可以取消");
        String from=task.getStatus();String reason=validatedReason(request.reason());task.cancel(access.userId(),access.username(),reason);
        try{taskRepository.saveAndFlush(task);event(access,task,"CANCEL",from,"CANCELLED",reason,requestId);}
        catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("取消盘点发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    @Transactional
    public WarehouseStockCountRecord reverse(String username,UUID id,WarehouseStockCountRecord.ReverseRequest request){
        CurrentWorkspaceAccess access=requireRole(username);String requestId=currentRequestId("count-reverse");
        WarehouseStockCountRecord duplicate=duplicateAction(access,requestId,id);if(duplicate!=null)return duplicate;
        StockCountTaskEntity task=requireTask(access,id);requireVersion(task.getVersion(),request.expectedVersion(),"盘点任务已经变化，请刷新后重试");
        requireStatus(task,"APPROVED","只有已审批盘点可以冲回");if(task.getAdjustmentMovementId()==null)throw conflict("零差异盘点没有库存调整，无需冲回");
        StockBalanceEntity balance=lockBalance(access,task.getBalanceId());requireVersion(balance.getVersion(),request.expectedBalanceVersion(),"库存已经变化，请刷新后重试");
        String type="RECEIPT".equals(task.getAdjustmentMovementType())?"ISSUE":"RECEIPT";BigDecimal quantity=task.getDifferenceQuantity().abs();
        if("ISSUE".equals(type)&&balance.availableQuantity().compareTo(quantity)<0)throw unprocessable("盘盈库存已被使用，不能冲回该盘点调整");
        String reason=validatedReason(request.reason());
        try{
            StockBalanceEntity.Change change=balance.apply(type,quantity,access.userId());balanceRepository.saveAndFlush(balance);
            StockMovementEntity movement=movement(access,balance,type,quantity,"盘点调整冲回 · "+reason,requestId+"-REVERSE",change,task,"REVERSE");
            task.reverse(movement,access.userId(),access.username(),reason);taskRepository.saveAndFlush(task);
            event(access,task,"REVERSE","APPROVED","REVERSED",reason,requestId);
        }catch(ObjectOptimisticLockingFailureException|DataIntegrityViolationException exception){throw conflict("盘点调整冲回发生并发冲突，请刷新确认结果");}
        return toRecord(task);
    }

    private StockMovementEntity movement(CurrentWorkspaceAccess access,StockBalanceEntity balance,String type,BigDecimal quantity,String reason,
            String requestId,StockBalanceEntity.Change change,StockCountTaskEntity task,String direction){
        StockMovementEntity movement=new StockMovementEntity(access.tenantOrganizationId(),access.workspaceId(),access.userId(),balance.getId(),
                nextMovementNumber(),type,quantity,reason,requestId,change);
        movement.attachSource("STOCK_COUNT_TASK",task.getId(),task.getCountNumber(),UUID.nameUUIDFromBytes((task.getId()+":"+direction).getBytes(StandardCharsets.UTF_8)));
        return movementRepository.saveAndFlush(movement);
    }
    private void event(CurrentWorkspaceAccess access,StockCountTaskEntity task,String action,String from,String to,String reason,String requestId){
        eventRepository.saveAndFlush(new StockCountEventEntity(access.tenantOrganizationId(),access.workspaceId(),task.getId(),action,from,to,reason,
                access.userId(),access.username(),requestId));
    }
    private WarehouseStockCountRecord duplicateAction(CurrentWorkspaceAccess access,String requestId,UUID taskId){
        var event=eventRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(),requestId);if(event.isEmpty())return null;
        if(!event.get().getTaskId().equals(taskId))throw conflict("请求编号已用于其他盘点动作");return toRecord(requireTask(access,taskId));
    }
    private StockCountTaskEntity requireTask(CurrentWorkspaceAccess access,UUID id){return taskRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(
            id,access.tenantOrganizationId(),access.workspaceId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"盘点任务不存在或不在当前工作区"));}
    private StockBalanceEntity lockBalance(CurrentWorkspaceAccess access,UUID id){return balanceRepository.findForUpdate(id,access.tenantOrganizationId())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"库存不存在或不在当前租户范围"));}
    private void validateBalance(CurrentWorkspaceAccess access,StockBalanceEntity balance){
        warehouseRepository.findByIdAndTenantOrganizationIdAndStatus(balance.getWarehouseId(),access.tenantOrganizationId(),"ACTIVE")
                .orElseThrow(()->unprocessable("库存所属仓库已停用或不存在"));
        locationRepository.findByIdAndTenantOrganizationIdAndStatus(balance.getLocationId(),access.tenantOrganizationId(),"ACTIVE")
                .orElseThrow(()->unprocessable("库存所属库位已停用或不存在"));
    }
    private void requireSnapshotVersion(StockCountTaskEntity task,StockBalanceEntity balance,long expected){
        requireVersion(balance.getVersion(),expected,"库存已经变化，旧盘点不能继续，请取消后重新盘点");
        requireVersion(balance.getVersion(),task.getSnapshotBalanceVersion(),"库存已在盘点期间变化，请取消后重新盘点");
    }
    private WarehouseStockCountRecord toRecord(StockCountTaskEntity task){
        StockBalanceEntity balance=balanceRepository.findByIdAndTenantOrganizationId(task.getBalanceId(),task.getTenantOrganizationId()).orElse(null);
        return new WarehouseStockCountRecord(task.getId(),task.getCountNumber(),task.getStatus(),task.getVersion(),task.getBalanceId(),
                balance==null?-1:balance.getVersion(),task.getWarehouseCode(),task.getWarehouseName(),task.getLocationCode(),task.getLocationName(),
                task.getMaterialCode(),task.getMaterialName(),task.getMaterialSpecification(),task.getLotNumber(),task.getUnit(),task.getQualityStatus(),
                task.getBookOnHand(),task.getBookAllocated(),task.getBookFrozen(),task.getCountedQuantity(),task.getDifferenceQuantity(),
                task.getSnapshotBalanceVersion(),task.getAdjustmentMovementId(),task.getAdjustmentMovementNumber(),task.getAdjustmentMovementType(),
                task.getReverseMovementId(),task.getReverseMovementNumber(),task.getReverseMovementType(),task.getCountNote(),task.getApprovalComment(),
                task.getCreatedByUsername(),task.getCreatedAt(),task.getCountedByUsername(),task.getCountedAt(),task.getApprovedByUsername(),
                task.getApprovedAt(),task.getCancelledByUsername(),task.getCancelledAt(),task.getCancellationReason(),task.getReversedByUsername(),
                task.getReversedAt(),task.getReversalReason(),task.getCreateRequestId());
    }
    private CurrentWorkspaceAccess requireRole(String username){CurrentWorkspaceAccess access=workspaceProvider.resolve(username);
        if(!ROLES.contains(access.roleCode()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前角色无权执行库存盘点");return access;}
    private String nextCountNumber(){Long value=jdbcTemplate.queryForObject("select nextval('warehouse.stock_count_number_seq')",Long.class);
        return "CNT-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",value);}
    private String nextMovementNumber(){Long value=jdbcTemplate.queryForObject("select nextval('warehouse.movement_number_seq')",Long.class);
        return "MOV-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",value);}
    private static String currentRequestId(String prefix){String id=MDC.get("requestId");return id==null||id.isBlank()?prefix+"-"+UUID.randomUUID():id;}
    private static void requireVersion(long current,long expected,String message){if(current!=expected)throw conflict(message);}
    private static void requireStatus(StockCountTaskEntity task,String status,String message){if(!status.equals(task.getStatus()))throw conflict(message);}
    private static String validatedReason(String reason){String value=normalize(reason);if(value.length()<4)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"原因至少填写 4 个字符");return value;}
    private static String normalize(String value){return value==null?"":value.trim();}
    private static String normalizeStatus(String value){if(value==null||value.isBlank()||"ALL".equalsIgnoreCase(value))return "";
        String result=value.trim().toUpperCase();if(!STATUSES.contains(result))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"盘点状态筛选无效");return result;}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException unprocessable(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
