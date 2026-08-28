package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true) @Import(TestcontainersConfiguration.class) @SpringBootTest
class PurchaseReturnIntegrationTest {
	private static final String USERNAME="lin.hao",PASSWORD="guanseq_dev",SUPPLIER="81000000-0000-4000-8000-000000000002",MATERIAL="42000000-0000-4000-8000-000000000004",WAREHOUSE="71000000-0000-4000-8000-000000000001",LOCATION="72000000-0000-4000-8000-000000000001";
	private final MockMvc mockMvc;private final JdbcTemplate jdbcTemplate;
	PurchaseReturnIntegrationTest(@Autowired WebApplicationContext context,@Autowired JdbcTemplate jdbcTemplate){this.mockMvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();this.jdbcTemplate=jdbcTemplate;}

	@Test @Transactional void shipsAndReversesAcceptedReturnAndFlagsPayableReview() throws Exception {
		Context source=createReceivedOrder(10,"purchase-return-main");
		MvcResult invoice=mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id","purchase-return-invoice").contentType(MediaType.APPLICATION_JSON).content("""
				{"purchaseOrderId":"%s","supplierInvoiceNumber":"SUP-RET-%s","invoiceDate":"%s","dueDate":"%s","lines":[{"purchaseOrderLineId":"%s","invoiceQuantity":10}]}
				""".formatted(source.orderId(),UUID.randomUUID(),LocalDate.now(),LocalDate.now().plusDays(30),source.orderLineId()))).andExpect(status().isOk()).andExpect(jsonPath("$.purchaseReturnImpactStatus").value("NONE")).andReturn();
		String invoiceId=field(invoice,"id");MvcResult references=mockMvc.perform(get("/api/v1/procurement/return-reference-data").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andReturn();
		String receiptLineId=findReceiptLine(references,source.orderId());
		MvcResult created=createReturn(source,receiptLineId,3,"purchase-return-create").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_SHIPMENT")).andReturn();String returnId=field(created,"id");
		createReturn(source,receiptLineId,3,"purchase-return-create").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(returnId));
		MvcResult shipped=act(returnId,"purchase-return-ship",0,"SHIP","仓库核对批次后退回供应商").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED")).andReturn();long version=((Number)JsonPath.parse(shipped.getResponse().getContentAsString()).read("$.version")).longValue();
		mockMvc.perform(get("/api/v1/procurement/orders/{id}",source.orderId()).with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].receivedQuantity").value(7));
		mockMvc.perform(get("/api/v1/finance/payable-invoices/{id}",invoiceId).with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.purchaseReturnImpactStatus").value("REVIEW_REQUIRED"));assertThat(stock()).isEqualTo(7);
		act(returnId,"purchase-return-stale",0,"REVERSE","使用旧版本冲回供应商退货").andExpect(status().isConflict());
		act(returnId,"purchase-return-reverse",version,"REVERSE","确认误操作并完整冲回库存").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED"));assertThat(stock()).isEqualTo(10);
		mockMvc.perform(get("/api/v1/finance/payable-invoices/{id}",invoiceId).with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.purchaseReturnImpactStatus").value("NONE"));
	}

	@Test @Transactional void allowsReplacementReceiptAfterAcceptedReturn() throws Exception {
		Context source=createReceivedOrder(5,"purchase-return-replacement");
		MvcResult references=mockMvc.perform(get("/api/v1/procurement/return-reference-data").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andReturn();
		String receiptLineId=findReceiptLine(references,source.orderId());
		MvcResult created=createReturn(source,receiptLineId,2,"purchase-return-replacement-create").andExpect(status().isOk()).andReturn();
		act(field(created,"id"),"purchase-return-replacement-ship",0,"SHIP","供应商退货出库后安排替换物料").andExpect(status().isOk());
		String body="""
			{"purchaseOrderId":"%s","warehouseId":"%s","locationId":"%s","note":"退货后的移动扫码补收","source":"MOBILE_SCAN","lines":[{"orderLineId":"%s","receivedQuantity":2,"lotNumber":"LOT-PUR-RET-REPLACEMENT"}]}
			""".formatted(source.orderId(),WAREHOUSE,LOCATION,source.orderLineId());
		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME,PASSWORD))
				.header("X-Request-Id","purchase-return-replacement-mobile").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.source").value("MOBILE_SCAN"))
				.andExpect(jsonPath("$.status").value("RECEIVED"));
		mockMvc.perform(get("/api/v1/procurement/orders/{id}",source.orderId()).with(httpBasic(USERNAME,PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].receivedQuantity").value(5))
				.andExpect(jsonPath("$.lines[0].outstandingQuantity").value(0));
		Integer grossReceived=jdbcTemplate.queryForObject("select received_quantity from procurement.purchase_order_lines where id=cast(? as uuid)",Integer.class,source.orderLineId());
		Integer returned=jdbcTemplate.queryForObject("select returned_quantity from procurement.purchase_order_lines where id=cast(? as uuid)",Integer.class,source.orderLineId());
		assertThat(grossReceived).isEqualTo(7);assertThat(returned).isEqualTo(2);
	}

	@Test @Transactional void rejectsExcessAndUnauthorizedReturnAndSupportsCancellation() throws Exception {
		Context source=createReceivedOrder(2,"purchase-return-guard");MvcResult references=mockMvc.perform(get("/api/v1/procurement/return-reference-data").with(httpBasic(USERNAME,PASSWORD))).andReturn();String receiptLineId=findReceiptLine(references,source.orderId());
		createReturn(source,receiptLineId,3,"purchase-return-excess").andExpect(status().isUnprocessableEntity());MvcResult created=createReturn(source,receiptLineId,1,"purchase-return-cancel-create").andExpect(status().isOk()).andReturn();act(field(created,"id"),"purchase-return-cancel",0,"CANCEL","供应商确认无需退回并取消授权").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		jdbcTemplate.update("update identity.workspace_memberships set role_code='PRODUCTION_OPERATOR' where id='30000000-0000-4000-8000-000000000101'");try{createReturn(source,receiptLineId,1,"purchase-return-denied").andExpect(status().isForbidden());}finally{jdbcTemplate.update("update identity.workspace_memberships set role_code='ADMIN' where id='30000000-0000-4000-8000-000000000101'");}
	}

	@Test @Transactional void shipsBlockedStockWithoutReducingAcceptedReceipt() throws Exception {
		String receiptBody="""
			{"purchaseOrderId":"82000000-0000-4000-8000-000000000001","warehouseId":"%s","locationId":"72000000-0000-4000-8000-000000000002","note":"采购退货不合格品测试","lines":[{"orderLineId":"83000000-0000-4000-8000-000000000001","receivedQuantity":5,"lotNumber":"LOT-PUR-RET-BLOCKED"}]}
			""".formatted(WAREHOUSE);
		MvcResult receipt=mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id","purchase-return-blocked-receipt").contentType(MediaType.APPLICATION_JSON).content(receiptBody)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_INSPECTION")).andReturn();
		String inspectionId=JsonPath.parse(receipt.getResponse().getContentAsString()).read("$.lines[0].inspectionId");
		mockMvc.perform(post("/api/v1/quality/incoming-inspections/{id}/complete",inspectionId).with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id","purchase-return-blocked-iqc").contentType(MediaType.APPLICATION_JSON).content("""
			{"acceptedQuantity":2,"rejectedQuantity":3,"inspector":"吴倩","defectDescription":"三件外观破损","conclusion":"两件放行，三件退回","expectedVersion":0}
			""")).andExpect(status().isOk());
		MvcResult orderBefore=mockMvc.perform(get("/api/v1/procurement/orders/82000000-0000-4000-8000-000000000001").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].receivedQuantity").value(2)).andReturn();
		long orderVersion=((Number)JsonPath.parse(orderBefore.getResponse().getContentAsString()).read("$.version")).longValue();
		MvcResult references=mockMvc.perform(get("/api/v1/procurement/return-reference-data").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andReturn();
		String receiptLineId=findReceiptLine(references,"82000000-0000-4000-8000-000000000001");
		String body="""
			{"purchaseOrderId":"82000000-0000-4000-8000-000000000001","expectedOrderVersion":%d,"returnDate":"%s","reason":"来料检验不合格退回供应商","lines":[{"purchaseReceiptLineId":"%s","qualityStatus":"BLOCKED","returnQuantity":2}]}
			""".formatted(orderVersion,LocalDate.now(),receiptLineId);
		MvcResult created=mockMvc.perform(post("/api/v1/procurement/returns").with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id","purchase-return-blocked-create").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.blockedReturnQuantity").value(2)).andReturn();
		act(field(created,"id"),"purchase-return-blocked-ship",0,"SHIP","不合格品隔离确认后退回供应商").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"));
		mockMvc.perform(get("/api/v1/procurement/orders/82000000-0000-4000-8000-000000000001").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].receivedQuantity").value(2));
		Integer blocked=jdbcTemplate.queryForObject("select on_hand_quantity from warehouse.stock_balances where lot_number='LOT-PUR-RET-BLOCKED' and quality_status='BLOCKED'",Integer.class);
		assertThat(blocked).isEqualTo(1);
	}

	private Context createReceivedOrder(int quantity,String prefix)throws Exception{LocalDate date=LocalDate.now().plusDays(5);String orderBody="""
			{"supplierId":"%s","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"%s","orderedQuantity":%d,"unitPrice":12}]}
			""".formatted(SUPPLIER,date,date,MATERIAL,quantity);MvcResult created=mockMvc.perform(post("/api/v1/procurement/orders").with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id",prefix+"-order").contentType(MediaType.APPLICATION_JSON).content(orderBody)).andExpect(status().isOk()).andReturn();String orderId=field(created,"id"),lineId=JsonPath.parse(created.getResponse().getContentAsString()).read("$.lines[0].id");orderAction(orderId,"SUBMIT",0);orderAction(orderId,"APPROVE",1);orderAction(orderId,"RELEASE",2);String receiptBody="""
			{"purchaseOrderId":"%s","warehouseId":"%s","locationId":"%s","note":"采购退货测试收货","lines":[{"orderLineId":"%s","receivedQuantity":%d,"lotNumber":"LOT-PUR-RET"}]}
			""".formatted(orderId,WAREHOUSE,LOCATION,lineId,quantity);mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id",prefix+"-receipt").contentType(MediaType.APPLICATION_JSON).content(receiptBody)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));MvcResult order=mockMvc.perform(get("/api/v1/procurement/orders/{id}",orderId).with(httpBasic(USERNAME,PASSWORD))).andReturn();long version=((Number)JsonPath.parse(order.getResponse().getContentAsString()).read("$.version")).longValue();return new Context(orderId,lineId,version);}
	private org.springframework.test.web.servlet.ResultActions createReturn(Context source,String receiptLineId,int quantity,String requestId)throws Exception{String body="""
			{"purchaseOrderId":"%s","expectedOrderVersion":%d,"returnDate":"%s","reason":"供应商物料质量异常安排退回","note":"自动化闭环","lines":[{"purchaseReceiptLineId":"%s","qualityStatus":"AVAILABLE","returnQuantity":%d}]}
			""".formatted(source.orderId(),source.version(),LocalDate.now(),receiptLineId,quantity);return mockMvc.perform(post("/api/v1/procurement/returns").with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id",requestId).contentType(MediaType.APPLICATION_JSON).content(body));}
	private org.springframework.test.web.servlet.ResultActions act(String id,String requestId,long version,String action,String reason)throws Exception{String body="""
			{"action":"%s","expectedVersion":%d,"reason":"%s"}
			""".formatted(action,version,reason);return mockMvc.perform(post("/api/v1/procurement/returns/{id}/actions",id).with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id",requestId).contentType(MediaType.APPLICATION_JSON).content(body));}
	private void orderAction(String id,String action,long version)throws Exception{String body="""
			{"action":"%s","expectedVersion":%d,"comment":"采购退货测试"}
			""".formatted(action,version);mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions",id).with(httpBasic(USERNAME,PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());}
	private String findReceiptLine(MvcResult result,String orderId)throws Exception{return JsonPath.parse(result.getResponse().getContentAsString()).read("$.orders[?(@.id=='"+orderId+"')].lines[0].purchaseReceiptLineId").toString().replace("[\"","").replace("\"]","");}
	private int stock(){Integer value=jdbcTemplate.queryForObject("select coalesce(sum(on_hand_quantity),0) from warehouse.stock_balances where lot_number='LOT-PUR-RET' and material_id=cast(? as uuid) and quality_status='AVAILABLE'",Integer.class,MATERIAL);return value==null?0:value;}
	private static String field(MvcResult result,String name)throws Exception{return JsonPath.parse(result.getResponse().getContentAsString()).read("$."+name);} private record Context(String orderId,String orderLineId,long version){}
}
