package com.guanseq.equipment.internal.telemetry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public final class ModbusTcpClient implements TelemetryProtocolAdapter {

	private final AtomicInteger transactionSequence = new AtomicInteger();

	@Override
	public String protocol() { return "MODBUS_TCP"; }

	@Override
	public Map<UUID, RawValue> readAll(ConnectionSpec connection, List<PointSpec> points) {
		if (points.isEmpty()) throw new TelemetryProtocolException("NO_POINTS", "连接没有可读取点位");
		int unitId;
		try {
			unitId = Integer.parseInt(connection.requiredParameter("unitId"));
		} catch (NumberFormatException exception) {
			throw new TelemetryProtocolException("INVALID_CONFIGURATION", "Modbus unitId 必须是整数", exception);
		}
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(connection.host(), connection.port()), connection.connectTimeoutMs());
			socket.setSoTimeout(connection.readTimeoutMs());
			socket.setTcpNoDelay(true);
			try (var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
					var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
				Map<UUID, RawValue> values = new LinkedHashMap<>();
				for (PointSpec point : points) {
					values.put(point.id(), readPoint(input, output, unitId, point));
				}
				return values;
			}
		} catch (SocketTimeoutException exception) {
			throw new TelemetryProtocolException("READ_TIMEOUT", "设备在限定时间内没有响应", exception);
		} catch (UnknownHostException exception) {
			throw new TelemetryProtocolException("UNKNOWN_HOST", "无法解析设备主机地址", exception);
		} catch (ConnectException exception) {
			throw new TelemetryProtocolException("CONNECTION_REFUSED", "无法建立设备连接", exception);
		} catch (IOException exception) {
			throw new TelemetryProtocolException("IO_ERROR", "设备通讯失败", exception);
		}
	}

	private RawValue readPoint(DataInputStream input, DataOutputStream output, int unitId, PointSpec point)
			throws IOException {
		int transactionId = transactionSequence.updateAndGet(value -> (value + 1) & 0xffff);
		int function = "COIL".equals(point.sourceType()) ? 1 : 3;
		int quantity = registerQuantity(point.valueType());
		int address;
		try {
			address = Integer.parseInt(point.sourceAddress());
		} catch (NumberFormatException exception) {
			throw new TelemetryProtocolException("INVALID_CONFIGURATION", "Modbus 点位地址必须是整数", exception);
		}
		output.writeShort(transactionId);
		output.writeShort(0);
		output.writeShort(6);
		output.writeByte(unitId);
		output.writeByte(function);
		output.writeShort(address);
		output.writeShort(quantity);
		output.flush();

		int responseTransaction = input.readUnsignedShort();
		int protocolId = input.readUnsignedShort();
		int length = input.readUnsignedShort();
		int responseUnit = input.readUnsignedByte();
		if (responseTransaction != transactionId || protocolId != 0 || responseUnit != unitId) {
			throw new TelemetryProtocolException("INVALID_RESPONSE", "设备响应标识与请求不一致");
		}
		if (length < 3 || length > 260) {
			throw new TelemetryProtocolException("INVALID_RESPONSE", "设备响应长度无效");
		}
		byte[] pdu = input.readNBytes(length - 1);
		if (pdu.length != length - 1) throw new EOFException("设备响应提前结束");
		int responseFunction = Byte.toUnsignedInt(pdu[0]);
		if (responseFunction == (function | 0x80)) {
			int exceptionCode = pdu.length > 1 ? Byte.toUnsignedInt(pdu[1]) : -1;
			throw new TelemetryProtocolException("MODBUS_EXCEPTION_" + exceptionCode,
					"设备返回 Modbus 异常码 " + exceptionCode);
		}
		if (responseFunction != function || pdu.length < 3) {
			throw new TelemetryProtocolException("INVALID_RESPONSE", "设备响应功能码或数据长度无效");
		}
		int byteCount = Byte.toUnsignedInt(pdu[1]);
		if (byteCount != pdu.length - 2) {
			throw new TelemetryProtocolException("INVALID_RESPONSE", "设备响应字节数无效");
		}
		if (function == 1) {
			if (byteCount < 1) throw new TelemetryProtocolException("INVALID_RESPONSE", "线圈响应为空");
			boolean value = (pdu[2] & 0x01) == 1;
			return new RawValue(Boolean.toString(value), null, value);
		}

		if (byteCount != quantity * 2) {
			throw new TelemetryProtocolException("INVALID_RESPONSE", "寄存器响应长度与点位类型不一致");
		}
		int first = unsignedRegister(pdu, 2);
		long raw = switch (point.valueType()) {
			case "UINT16" -> first;
			case "INT16" -> (short) first;
			case "UINT32" -> ((long) first << 16) | unsignedRegister(pdu, 4);
			case "INT32" -> (int) (((long) first << 16) | unsignedRegister(pdu, 4));
			default -> throw new TelemetryProtocolException("UNSUPPORTED_VALUE_TYPE", "不支持的寄存器值类型");
		};
		return new RawValue(Long.toString(raw), BigDecimal.valueOf(raw), null);
	}

	private static int registerQuantity(String valueType) {
		return switch (valueType) {
			case "BOOLEAN", "UINT16", "INT16" -> 1;
			case "UINT32", "INT32" -> 2;
			default -> throw new TelemetryProtocolException("UNSUPPORTED_VALUE_TYPE", "不支持的点位值类型");
		};
	}

	private static int unsignedRegister(byte[] bytes, int offset) {
		if (offset + 1 >= bytes.length) {
			throw new TelemetryProtocolException("INVALID_RESPONSE", "寄存器响应数据不足");
		}
		return (Byte.toUnsignedInt(bytes[offset]) << 8) | Byte.toUnsignedInt(bytes[offset + 1]);
	}

}
