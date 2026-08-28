package com.guanseq.equipment.internal.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ModbusTcpClientTest {

	@Test
	void readsCoilsAndRegistersOverTheProductionProtocolImplementation() throws Exception {
		try (ProtocolEndpoint endpoint = new ProtocolEndpoint()) {
			UUID runState = UUID.randomUUID();
			UUID spindleLoad = UUID.randomUUID();
			UUID doorClosed = UUID.randomUUID();
			var values = new ModbusTcpClient().readAll(
					new TelemetryProtocolAdapter.ConnectionSpec("127.0.0.1", endpoint.port(), 500, 500,
							Map.of("unitId", "1")),
					List.of(new TelemetryProtocolAdapter.PointSpec(runState, "HOLDING_REGISTER", "0", "UINT16"),
							new TelemetryProtocolAdapter.PointSpec(spindleLoad, "HOLDING_REGISTER", "2", "UINT16"),
							new TelemetryProtocolAdapter.PointSpec(doorClosed, "COIL", "0", "BOOLEAN")));

			assertThat(values.get(runState).numericValue()).isEqualByComparingTo("2");
			assertThat(values.get(spindleLoad).numericValue()).isEqualByComparingTo("685");
			assertThat(values.get(doorClosed).booleanValue()).isTrue();
		}
	}

	@Test
	void exposesStableModbusExceptionCodes() throws Exception {
		try (ProtocolEndpoint endpoint = new ProtocolEndpoint()) {
			UUID missing = UUID.randomUUID();
			assertThatThrownBy(() -> new ModbusTcpClient().readAll(
					new TelemetryProtocolAdapter.ConnectionSpec("127.0.0.1", endpoint.port(), 500, 500,
							Map.of("unitId", "1")),
					List.of(new TelemetryProtocolAdapter.PointSpec(missing, "HOLDING_REGISTER", "99", "UINT16"))))
					.isInstanceOf(TelemetryProtocolAdapter.TelemetryProtocolException.class)
					.satisfies(error -> assertThat(((TelemetryProtocolAdapter.TelemetryProtocolException) error).code())
							.isEqualTo("MODBUS_EXCEPTION_2"));
		}
	}

	private static final class ProtocolEndpoint implements AutoCloseable {
		private final ServerSocket server;
		private volatile boolean running = true;

		private ProtocolEndpoint() throws IOException {
			server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
			Thread.startVirtualThread(this::acceptLoop);
		}

		int port() { return server.getLocalPort(); }

		private void acceptLoop() {
			while (running) {
				try {
					Socket socket = server.accept();
					Thread.startVirtualThread(() -> handle(socket));
				} catch (IOException exception) {
					if (running) throw new IllegalStateException(exception);
				}
			}
		}

		private void handle(Socket socket) {
			try (socket;
					var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
					var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
				while (running) {
					int transactionId;
					try {
						transactionId = input.readUnsignedShort();
					} catch (EOFException exception) {
						return;
					}
					input.readUnsignedShort();
					input.readUnsignedShort();
					int unitId = input.readUnsignedByte();
					int function = input.readUnsignedByte();
					int address = input.readUnsignedShort();
					int quantity = input.readUnsignedShort();
					if (address == 99) {
						output.writeShort(transactionId);
						output.writeShort(0);
						output.writeShort(3);
						output.writeByte(unitId);
						output.writeByte(function | 0x80);
						output.writeByte(2);
					} else if (function == 1) {
						writeHeader(output, transactionId, unitId, function, 1);
						output.writeByte(address == 0 ? 1 : 0);
					} else {
						writeHeader(output, transactionId, unitId, function, quantity * 2);
						for (int index = 0; index < quantity; index++) {
							int register = address + index == 0 ? 2 : address + index == 2 ? 685 : 0;
							output.writeShort(register);
						}
					}
					output.flush();
				}
			} catch (IOException ignored) {
				// Client closes after the batch.
			}
		}

		private static void writeHeader(DataOutputStream output, int transactionId, int unitId,
				int function, int byteCount) throws IOException {
			output.writeShort(transactionId);
			output.writeShort(0);
			output.writeShort(3 + byteCount);
			output.writeByte(unitId);
			output.writeByte(function);
			output.writeByte(byteCount);
		}

		@Override
		public void close() throws IOException {
			running = false;
			server.close();
		}
	}
}
