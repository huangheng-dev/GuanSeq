import net from "node:net";

const host = process.env.GUANSEQ_MQTT_SIM_HOST ?? "127.0.0.1";
const port = Number(process.env.GUANSEQ_MQTT_SIM_PORT ?? "1883");
const topic = process.env.GUANSEQ_MQTT_SIM_TOPIC ?? "factory/cnc/telemetry";
const intervalMs = Number(process.env.GUANSEQ_MQTT_SIM_INTERVAL_MS ?? "2000");

if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("GUANSEQ_MQTT_SIM_PORT 必须是 1–65535 的整数");
if (!Number.isInteger(intervalMs) || intervalMs < 250) throw new Error("GUANSEQ_MQTT_SIM_INTERVAL_MS 必须是不小于 250 的整数");
if (!topic || topic.includes("#") || topic.includes("+")) throw new Error("GUANSEQ_MQTT_SIM_TOPIC 必须是精确 Topic");

const clients = new Set();
let sequence = 0;

function remainingLength(value) {
  const bytes = [];
  do {
    let digit = value % 128;
    value = Math.floor(value / 128);
    if (value > 0) digit |= 0x80;
    bytes.push(digit);
  } while (value > 0);
  return Buffer.from(bytes);
}

function stringField(value) {
  const encoded = Buffer.from(value, "utf8");
  const prefix = Buffer.alloc(2);
  prefix.writeUInt16BE(encoded.length);
  return Buffer.concat([prefix, encoded]);
}

function packet(type, payload = Buffer.alloc(0)) {
  return Buffer.concat([Buffer.from([type]), remainingLength(payload.length), payload]);
}

function publish(socket) {
  if (!socket.writable || !socket.subscriptions?.has(topic)) return;
  sequence += 1;
  const payload = Buffer.from(JSON.stringify({
    messageId: `dev-${String(sequence).padStart(8, "0")}`,
    deviceTime: new Date().toISOString(),
    values: { runState: 2, spindleLoad: 64 + (sequence % 20) / 2, doorClosed: sequence % 10 !== 0 },
  }));
  socket.write(packet(0x30, Buffer.concat([stringField(topic), payload])));
}

function readPacket(buffer) {
  if (buffer.length < 2) return null;
  let multiplier = 1; let length = 0; let index = 1;
  for (; index < buffer.length && index <= 4; index += 1) {
    const digit = buffer[index];
    length += (digit & 0x7f) * multiplier;
    if ((digit & 0x80) === 0) {
      const start = index + 1; const end = start + length;
      if (buffer.length < end) return null;
      return { header: buffer[0], payload: buffer.subarray(start, end), rest: buffer.subarray(end) };
    }
    multiplier *= 128;
  }
  return null;
}

function handle(socket, incoming) {
  const type = incoming.header >> 4;
  if (type === 1) {
    socket.write(packet(0x20, Buffer.from([0, 0])));
    return;
  }
  if (type === 8) {
    const payload = incoming.payload;
    if (payload.length < 5) return socket.destroy(new Error("无效 MQTT SUBSCRIBE"));
    const packetId = payload.readUInt16BE(0); let offset = 2; const grants = [];
    while (offset + 3 <= payload.length) {
      const length = payload.readUInt16BE(offset); offset += 2;
      if (offset + length + 1 > payload.length) return socket.destroy(new Error("无效 MQTT Topic"));
      const requestedTopic = payload.subarray(offset, offset + length).toString("utf8"); offset += length;
      const qos = payload[offset]; offset += 1;
      socket.subscriptions.add(requestedTopic); grants.push(Math.min(qos, 1));
    }
    const id = Buffer.alloc(2); id.writeUInt16BE(packetId);
    socket.write(packet(0x90, Buffer.concat([id, Buffer.from(grants)])));
    publish(socket);
    return;
  }
  if (type === 12) socket.write(packet(0xd0));
  if (type === 14) socket.end();
}

const server = net.createServer((socket) => {
  socket.subscriptions = new Set(); clients.add(socket);
  let pending = Buffer.alloc(0);
  socket.on("data", (chunk) => {
    pending = Buffer.concat([pending, chunk]);
    while (pending.length) {
      const incoming = readPacket(pending);
      if (!incoming) return;
      pending = incoming.rest; handle(socket, incoming);
    }
  });
  socket.on("close", () => clients.delete(socket));
  socket.on("error", () => undefined);
});

const timer = setInterval(() => clients.forEach(publish), intervalMs);
server.listen(port, host, () => {
  process.stdout.write(`GuanSeq MQTT 3.1.1 开发 Broker 已启动：${host}:${port}，topic=${topic}\n`);
  process.stdout.write("仅用于开发/测试；生产环境由用户选择并运维外部 Broker，仿真通过不代表现场验收通过。\n");
});

function shutdown() { clearInterval(timer); clients.forEach((socket) => socket.destroy()); server.close(() => process.exit(0)); }
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
