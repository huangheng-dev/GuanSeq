package com.guanseq.equipment.internal.telemetry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MqttTestBroker implements AutoCloseable {

	private final ServerSocket server;
	private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
	private volatile boolean running = true;
	private volatile String payload;

	public MqttTestBroker(String payload) throws IOException {
		this.payload = payload;
		this.server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
		Thread.startVirtualThread(this::acceptLoop);
	}

	public int port() { return server.getLocalPort(); }

	public void publish(String nextPayload) {
		this.payload = nextPayload;
		for (Subscriber subscriber : subscribers) subscriber.publish(nextPayload);
	}

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
		Subscriber subscriber = null;
		try (socket;
				var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
				var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
			while (running) {
				int header;
				try {
					header = input.readUnsignedByte();
				} catch (EOFException exception) {
					return;
				}
				int remaining = readRemainingLength(input);
				byte[] body = input.readNBytes(remaining);
				if (body.length != remaining) return;
				int type = header >>> 4;
				if (type == 1) {
					writePacket(output, 0x20, new byte[] { 0, 0 });
				} else if (type == 8) {
					int packetId = (Byte.toUnsignedInt(body[0]) << 8) | Byte.toUnsignedInt(body[1]);
					int topicLength = (Byte.toUnsignedInt(body[2]) << 8) | Byte.toUnsignedInt(body[3]);
					String topic = new String(body, 4, topicLength, StandardCharsets.UTF_8);
					int qos = Byte.toUnsignedInt(body[4 + topicLength]);
					writePacket(output, 0x90, new byte[] { (byte) (packetId >>> 8), (byte) packetId, (byte) qos });
					subscriber = new Subscriber(output, topic);
					subscribers.add(subscriber);
					if (payload != null) subscriber.publish(payload);
				} else if (type == 12) {
					writePacket(output, 0xd0, new byte[0]);
				} else if (type == 14) {
					return;
				}
			}
		} catch (IOException ignored) {
			// 客户端主动断开或测试结束。
		} finally {
			if (subscriber != null) subscribers.remove(subscriber);
		}
	}

	private static int readRemainingLength(DataInputStream input) throws IOException {
		int multiplier = 1;
		int value = 0;
		int encoded;
		do {
			encoded = input.readUnsignedByte();
			value += (encoded & 127) * multiplier;
			multiplier *= 128;
			if (multiplier > 128 * 128 * 128 * 128) throw new IOException("MQTT remaining length 无效");
		} while ((encoded & 128) != 0);
		return value;
	}

	private static synchronized void writePacket(DataOutputStream output, int header, byte[] body)
			throws IOException {
		output.writeByte(header);
		writeRemainingLength(output, body.length);
		output.write(body);
		output.flush();
	}

	private static void writeRemainingLength(DataOutputStream output, int length) throws IOException {
		int value = length;
		do {
			int encoded = value % 128;
			value /= 128;
			if (value > 0) encoded |= 128;
			output.writeByte(encoded);
		} while (value > 0);
	}

	@Override
	public void close() throws IOException {
		running = false;
		server.close();
		for (Subscriber subscriber : subscribers) subscriber.close();
		subscribers.clear();
	}

	private static final class Subscriber {
		private final DataOutputStream output;
		private final String topic;

		private Subscriber(DataOutputStream output, String topic) {
			this.output = output;
			this.topic = topic;
		}

		private synchronized void publish(String payload) {
			try {
				byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
				byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
				byte[] body = new byte[2 + topicBytes.length + payloadBytes.length];
				body[0] = (byte) (topicBytes.length >>> 8);
				body[1] = (byte) topicBytes.length;
				System.arraycopy(topicBytes, 0, body, 2, topicBytes.length);
				System.arraycopy(payloadBytes, 0, body, 2 + topicBytes.length, payloadBytes.length);
				writePacket(output, 0x31, body);
			} catch (IOException ignored) {
				// 连接已关闭，处理线程会移除订阅者。
			}
		}

		private void close() {
			try { output.close(); } catch (IOException ignored) { }
		}
	}
}
