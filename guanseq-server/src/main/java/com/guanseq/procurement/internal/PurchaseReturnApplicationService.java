package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.procurement.api.PurchaseReturnChangedEvent;
import com.guanseq.procurement.api.PurchaseReturnPage;
import com.guanseq.procurement.api.PurchaseReturnRecord;
import com.guanseq.procurement.api.PurchaseReturnReferenceData;
import com.guanseq.warehouse.api.PurchaseReturnStockService;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PurchaseReturnApplicationService {
	private static final Set<String> CREATE_ROLES=Set.of("PROCUREMENT_MANAGER","ADMIN");
	private static final Set<String> SHIP_ROLES=Set.of("WAREHOUSE_MANAGER","INVENTORY_CONTROLLER","ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final PurchaseOrderRepository orderRepository;
	private final PurchaseReceiptLineRepository receiptLineRepository;
	private final PurchaseReturnRepository returnRepository;
	private final PurchaseReturnLineRepository returnLineRepository;
	private final PurchaseReturnEventRepository eventRepository;
	private final PurchaseReturnStockService stockService;
	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher eventPublisher;
	PurchaseReturnApplicationService(CurrentWorkspaceProvider workspaceProvider,PurchaseOrderRepository orderRepository,
			PurchaseReceiptLineRepository receiptLineRepository,PurchaseReturnRepository returnRepository,
			PurchaseReturnLineRepository returnLineRepository,PurchaseReturnEventRepository eventRepository,
			PurchaseReturnStockService stockService,JdbcTemplate jdbcTemplate,ApplicationEventPublisher eventPublisher){
		this.workspaceProvider=workspaceProvider;this.orderRepository=orderRepository;this.receiptLineRepository=receiptLineRepository;
		this.returnRepository=returnRepository;this.returnLineRepository=returnLineRepository;this.eventRepository=eventRepository;
		this.stockService=stockService;this.jdbcTemplate=jdbcTemplate;this.eventPublisher=eventPublisher;
	}

	@Transactional(readOnly=true)
	public PurchaseReturnPage list(String username,String query,String status,int page,int size){
		CurrentWorkspaceAccess access=workspaceProvider.resolve(username);
		var result=returnRepository.search(access.tenantOrganizationId(),normalize(query),normalizeStatus(status),
				PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"createdAt")));
		return new PurchaseReturnPage(result.getContent().stream().map(item->toRecord(access,item)).toList(),result.getTotalElements(),
				result.getNumber(),result.getSize(),result.getTotalPages(),CREATE_ROLES.contains(access.roleCode()));
	}
	@Transactional(readOnly=true)
	public PurchaseReturnRecord get(String username,UUID id){CurrentWorkspaceAccess access=workspaceProvider.resolve(username);return toRecord(access,requireReturn(access,id));}

	@Transactional(readOnly=true)
	public PurchaseReturnReferenceData referenceData(String username){
		CurrentWorkspaceAccess access=workspaceProvider.resolve(username);
		Map<UUID,MutableOrder> orders=new LinkedHashMap<>();
		for(PurchaseReceiptLineEntity line:receiptLineRepository.findByTenantOrganizationId(access.tenantOrganizationId())){
			PurchaseReceiptEntity receipt=line.getReceipt();
			PurchaseOrderEntity order=orderRepository.findByIdAndTenantOrganizationId(receipt.getPurchaseOrderId(),access.tenantOrganizationId()).orElse(null);
			if(order==null)continue;
			addReference(access,orders,order,receipt,line,"AVAILABLE",line.getAcceptedQuantity(),line.getAcceptedBalanceId());
			addReference(access,orders,order,receipt,line,"BLOCKED",line.getRejectedQuantity(),line.getRejectedBalanceId());
		}
		List<PurchaseReturnReferenceData.ReturnableOrder> result=orders.values().stream().filter(item->!item.lines.isEmpty())
				.map(item->new PurchaseReturnReferenceData.ReturnableOrder(item.order.getId(),item.order.getOrderNumber(),item.order.getSupplierId(),
						item.order.getSupplierCode(),item.order.getSupplierName(),item.order.getVersion(),item.lines)).toList();
		return new PurchaseReturnReferenceData(result,CREATE_ROLES.contains(access.roleCode()));
	}
	private void addReference(CurrentWorkspaceAccess access,Map<UUID,MutableOrder> orders,PurchaseOrderEntity order,PurchaseReceiptEntity receipt,
			PurchaseReceiptLineEntity line,String quality,BigDecimal sourceQuantity,UUID balanceId){
		if(sourceQuantity==null||sourceQuantity.signum()<=0||balanceId==null)return;
		var availability=stockService.findAvailability(access.tenantOrganizationId(),balanceId).orElse(null);
		if(availability==null||!quality.equals(availability.qualityStatus()))return;
		BigDecimal pending=returnLineRepository.sumActive(access.tenantOrganizationId(),line.getId(),quality);
		BigDecimal returnable=sourceQuantity.subtract(pending).min(availability.returnableQuantity()).max(BigDecimal.ZERO);
		if(returnable.signum()<=0)return;
		MutableOrder target=orders.computeIfAbsent(order.getId(),ignored->new MutableOrder(order));
		target.lines.add(new PurchaseReturnReferenceData.ReturnableLine(line.getId(),receipt.getReceiptNumber(),line.getPurchaseOrderLineId(),
				line.getMaterialId(),line.getMaterialCode(),line.getMaterialName(),line.getMaterialSpecification(),line.getUnit(),quality,balanceId,
				availability.warehouseCode(),availability.locationCode(),availability.lotNumber(),sourceQuantity,pending,availability.returnableQuantity(),returnable));
	}

	@Transactional
	public PurchaseReturnRecord create(String username,PurchaseReturnRecord.CreateRequest request){
		CurrentWorkspaceAccess access=workspaceProvider.resolve(username);requireRole(access,CREATE_ROLES,"建立采购退货授权");
		String requestId=requestId("purchase-return-create-");
		var duplicate=returnRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(),requestId);if(duplicate.isPresent())return toRecord(access,duplicate.get());
		lock("purchase-return-order:"+request.purchaseOrderId());
		duplicate=returnRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(),requestId);if(duplicate.isPresent())return toRecord(access,duplicate.get());
		PurchaseOrderEntity order=orderRepository.findByIdAndTenantOrganizationId(request.purchaseOrderId(),access.tenantOrganizationId())
				.orElseThrow(()->invalid("采购订单不存在或不在当前租户范围"));
		if(order.getVersion()!=request.expectedOrderVersion())throw conflict("采购订单已变化，请刷新后重试");
		Map<UUID,PurchaseReceiptLineEntity> sources=receiptLineRepository.findByTenantOrganizationId(access.tenantOrganizationId()).stream()
				.filter(line->line.getReceipt().getPurchaseOrderId().equals(order.getId())).collect(Collectors.toMap(PurchaseReceiptLineEntity::getId,Function.identity()));
		PurchaseReturnEntity result=new PurchaseReturnEntity(access.tenantOrganizationId(),access.operatingOrganizationId(),access.workspaceId(),
				nextNumber(),order,request.returnDate(),request.reason().trim(),blankToNull(request.note()),requestId,access.userId());
		Set<String> unique=new HashSet<>();
		for(PurchaseReturnRecord.LineInput input:request.lines()){
			String key=input.purchaseReceiptLineId()+":"+input.qualityStatus();if(!unique.add(key))throw invalid("同一收货行和质量状态不能重复");
			PurchaseReceiptLineEntity source=sources.get(input.purchaseReceiptLineId());if(source==null)throw invalid("退货行不属于指定采购订单");
			BigDecimal sourceQuantity="AVAILABLE".equals(input.qualityStatus())?source.getAcceptedQuantity():source.getRejectedQuantity();
			UUID balanceId="AVAILABLE".equals(input.qualityStatus())?source.getAcceptedBalanceId():source.getRejectedBalanceId();
			if(balanceId==null)throw invalid("原收货行没有对应质量状态的库存余额");
			BigDecimal pending=returnLineRepository.sumActive(access.tenantOrganizationId(),source.getId(),input.qualityStatus());
			BigDecimal quantity=input.returnQuantity();
			var availability=stockService.findAvailability(access.tenantOrganizationId(),balanceId).orElseThrow(()->invalid("原收货库存余额不存在"));
			BigDecimal allowed=sourceQuantity.subtract(pending).min(availability.returnableQuantity()).max(BigDecimal.ZERO);
			if(quantity.signum()<=0||quantity.compareTo(allowed)>0)throw invalid(source.getMaterialCode()+" 本次退货数量超过可退数量 "+allowed.stripTrailingZeros().toPlainString());
			result.addLine(source,input.qualityStatus(),balanceId,quantity);
		}
		returnRepository.saveAndFlush(result);audit(access,result,"CREATED",null,"PENDING_SHIPMENT",request.reason(),requestId,Map.of("lineCount",result.getLines().size()));
		return toRecord(access,result);
	}

	@Transactional
	public PurchaseReturnRecord act(String username,UUID id,PurchaseReturnRecord.ActionRequest request){
		CurrentWorkspaceAccess access=workspaceProvider.resolve(username);String requestId=requestId("purchase-return-action-");
		var duplicate=eventRepository.findByTenantOrganizationIdAndRequestId(access.tenantOrganizationId(),requestId);
		if(duplicate.isPresent()){
			if(!duplicate.get().getReturnId().equals(id))throw conflict("请求编号已用于其他采购退货动作");
			return toRecord(access,requireReturn(access,id));
		}
		PurchaseReturnEntity result=requireReturn(access,id);if(result.getVersion()!=request.expectedVersion())throw conflict("采购退货单已被其他事务更新，请刷新后重试");
		String from=result.getStatus();
		switch(request.action()){
			case "CANCEL"->{requireRole(access,CREATE_ROLES,"取消采购退货授权");if(!"PENDING_SHIPMENT".equals(from))throw conflict("只有待出库退货单可以取消");result.transition("CANCELLED",access.userId());}
			case "SHIP"->{requireRole(access,SHIP_ROLES,"执行供应商退回出库");if(!"PENDING_SHIPMENT".equals(from))throw conflict("只有待出库退货单可以过账");postShipment(username,access,result,request.reason(),requestId,false);result.transition("SHIPPED",access.userId());}
			case "REVERSE"->{requireRole(access,SHIP_ROLES,"冲回供应商退回出库");if(!"SHIPPED".equals(from))throw conflict("只有已出库退货单可以冲回");postShipment(username,access,result,request.reason(),requestId,true);result.transition("REVERSED",access.userId());}
			default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不支持的采购退货动作");
		}
		returnRepository.saveAndFlush(result);audit(access,result,request.action(),from,result.getStatus(),request.reason(),requestId,Map.of());return toRecord(access,result);
	}
	private void postShipment(String username,CurrentWorkspaceAccess access,PurchaseReturnEntity result,String reason,String requestId,boolean reverse){
		lock("purchase-return-order:"+result.getPurchaseOrderId());
		PurchaseOrderEntity order=orderRepository.findByIdAndTenantOrganizationId(result.getPurchaseOrderId(),access.tenantOrganizationId()).orElseThrow();
		Map<UUID,PurchaseOrderLineEntity> orderLines=order.getLines().stream().collect(Collectors.toMap(PurchaseOrderLineEntity::getId,Function.identity()));
		for(PurchaseReturnLineEntity line:result.getLines()){
			var command=new PurchaseReturnStockService.ReturnCommand(access.tenantOrganizationId(),access.workspaceId(),access.userId(),line.getStockBalanceId(),
					line.getQualityStatus(),line.getAuthorizedQuantity(),result.getId(),result.getReturnNumber(),line.getId(),reason,requestId+"-"+line.getLineNumber());
			var movement=reverse?stockService.reverse(command):stockService.ship(command);
			if(reverse)line.clearShipment();else line.markShipped(movement.movementId(),movement.warehouseCode(),movement.locationCode(),movement.lotNumber());
			if("AVAILABLE".equals(line.getQualityStatus())){
				PurchaseOrderLineEntity orderLine=orderLines.get(line.getPurchaseOrderLineId());if(orderLine==null)throw invalid("采购订单行不存在");
				if(reverse)orderLine.reverseSupplierReturn(line.getAuthorizedQuantity());else orderLine.applySupplierReturn(line.getAuthorizedQuantity());
			}
		}
		orderRepository.saveAndFlush(order);
		if(result.getAcceptedReturnQuantity().signum()>0)eventPublisher.publishEvent(new PurchaseReturnChangedEvent(username,order.getId(),
				reverse?"PURCHASE_RETURN_REVERSAL":"PURCHASE_RETURN_SHIPMENT",result.getReturnNumber()));
	}

	private PurchaseReturnRecord toRecord(CurrentWorkspaceAccess access,PurchaseReturnEntity item){
		List<String> actions=new ArrayList<>();if("PENDING_SHIPMENT".equals(item.getStatus())){if(CREATE_ROLES.contains(access.roleCode()))actions.add("CANCEL");if(SHIP_ROLES.contains(access.roleCode()))actions.add("SHIP");}
		else if("SHIPPED".equals(item.getStatus())&&SHIP_ROLES.contains(access.roleCode()))actions.add("REVERSE");
		return new PurchaseReturnRecord(item.getId(),item.getReturnNumber(),item.getPurchaseOrderId(),item.getOrderNumber(),item.getSupplierId(),item.getSupplierCode(),item.getSupplierName(),item.getReturnDate(),item.getStatus(),item.getReason(),item.getNote(),item.getTotalReturnQuantity(),item.getAcceptedReturnQuantity(),item.getBlockedReturnQuantity(),item.getVersion(),item.getCreatedAt(),item.getUpdatedAt(),actions,
				item.getLines().stream().sorted(Comparator.comparingInt(PurchaseReturnLineEntity::getLineNumber)).map(line->new PurchaseReturnRecord.Line(line.getId(),line.getPurchaseReceiptLineId(),line.getPurchaseOrderLineId(),line.getLineNumber(),line.getMaterialId(),line.getMaterialCode(),line.getMaterialName(),line.getMaterialSpecification(),line.getUnit(),line.getQualityStatus(),line.getAuthorizedQuantity(),line.getShippedQuantity(),line.getStockBalanceId(),line.getStockMovementId(),line.getWarehouseCode(),line.getLocationCode(),line.getLotNumber())).toList(),
				eventRepository.findByReturnIdOrderByOccurredAtDesc(item.getId()).stream().map(event->new PurchaseReturnRecord.Event(event.getId(),event.getAction(),event.getFromStatus(),event.getToStatus(),event.getReason(),event.getRequestId(),event.getOccurredAt())).toList());
	}
	private void audit(CurrentWorkspaceAccess access,PurchaseReturnEntity item,String action,String from,String to,String reason,String requestId,Map<String,Object> details){eventRepository.saveAndFlush(new PurchaseReturnEventEntity(access.tenantOrganizationId(),access.workspaceId(),access.userId(),item.getId(),action,from,to,reason.trim(),requestId,details));}
	private PurchaseReturnEntity requireReturn(CurrentWorkspaceAccess access,UUID id){return returnRepository.findByIdAndTenantOrganizationId(id,access.tenantOrganizationId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"采购退货单不存在或不在当前租户范围"));}
	private void lock(String key){jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?,0))",statement->statement.setString(1,key),rs->null);}
	private String nextNumber(){Long value=jdbcTemplate.queryForObject("select nextval('procurement.purchase_return_number_seq')",Long.class);return "PUR-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%06d",value);}
	private static void requireRole(CurrentWorkspaceAccess access,Set<String> roles,String action){if(!roles.contains(access.roleCode()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前角色无权"+action);}
	private static String requestId(String prefix){String value=MDC.get("requestId");return value==null||value.isBlank()?prefix+UUID.randomUUID():value;}
	private static String normalize(String value){return value==null?"":value.trim();} private static String normalizeStatus(String value){return value==null||value.isBlank()||"ALL".equalsIgnoreCase(value)?"":value.trim().toUpperCase();}
	private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
	private static ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);} private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
	private static final class MutableOrder{private final PurchaseOrderEntity order;private final List<PurchaseReturnReferenceData.ReturnableLine> lines=new ArrayList<>();private MutableOrder(PurchaseOrderEntity order){this.order=order;}}
}
