import net from "node:net";

const host = process.env.GUANSEQ_MODBUS_SIM_HOST ?? "127.0.0.1";
const port = Number(process.env.GUANSEQ_MODBUS_SIM_PORT ?? "1502");
const unitId = Number(process.env.GUANSEQ_MODBUS_SIM_UNIT_ID ?? "1");

if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("GUANSEQ_MODBUS_SIM_PORT 必须是 1–65535 的整数");
if (!Number.isInteger(unitId) || unitId < 0 || unitId > 247) throw new Error("GUANSEQ_MODBUS_SIM_UNIT_ID 必须是 0–247 的整数");

const holdingRegisters = new Map([
  [0, 2],       // HR40001 运行状态：2=运行
  [1, 3200],    // HR40002 主轴转速 rpm
  [2, 685],     // HR40003 主轴负载：×0.1 = 68.5%
  [3, 1200],    // HR40004 进给速度 mm/min
  [9, 0],       // HR40010 累计产量高字
  [10, 42],     // HR40011 累计产量低字
  [11, 0],      // HR40012 当前报警码
]);
const coils = new Map([[0, true], [1, false]]); // C00001 防护门关闭；C00002 冷却液低位

function exceptionResponse(request, code) {
  const response = Buffer.alloc(9);
  response.writeUInt16BE(request.transactionId, 0);
  response.writeUInt16BE(0, 2);
  response.writeUInt16BE(3, 4);
  response.writeUInt8(request.unitId, 6);
  response.writeUInt8(request.functionCode | 0x80, 7);
  response.writeUInt8(code, 8);
  return response;
}

function readRequest(frame) {
  return {
    transactionId: frame.readUInt16BE(0),
    protocolId: frame.readUInt16BE(2),
    unitId: frame.readUInt8(6),
    functionCode: frame.readUInt8(7),
    address: frame.readUInt16BE(8),
    quantity: frame.readUInt16BE(10),
  };
}

function respond(frame) {
  const request = readRequest(frame);
  if (request.protocolId !== 0 || request.unitId !== unitId) return exceptionResponse(request, 11);
  if (request.functionCode === 1) {
    if (request.quantity < 1 || request.quantity > 2000) return exceptionResponse(request, 3);
    const byteCount = Math.ceil(request.quantity / 8);
    const response = Buffer.alloc(9 + byteCount);
    response.writeUInt16BE(request.transactionId, 0);
    response.writeUInt16BE(0, 2);
    response.writeUInt16BE(3 + byteCount, 4);
    response.writeUInt8(request.unitId, 6);
    response.writeUInt8(1, 7);
    response.writeUInt8(byteCount, 8);
    for (let index = 0; index < request.quantity; index += 1) {
      if (coils.get(request.address + index) === true) response[9 + Math.floor(index / 8)] |= 1 << (index % 8);
    }
    return response;
  }
  if (request.functionCode === 3) {
    if (request.quantity < 1 || request.quantity > 125) return exceptionResponse(request, 3);
    const byteCount = request.quantity * 2;
    const response = Buffer.alloc(9 + byteCount);
    response.writeUInt16BE(request.transactionId, 0);
    response.writeUInt16BE(0, 2);
    response.writeUInt16BE(3 + byteCount, 4);
    response.writeUInt8(request.unitId, 6);
    response.writeUInt8(3, 7);
    response.writeUInt8(byteCount, 8);
    for (let index = 0; index < request.quantity; index += 1) {
      response.writeUInt16BE(holdingRegisters.get(request.address + index) ?? 0, 9 + index * 2);
    }
    return response;
  }
  return exceptionResponse(request, 1);
}

const server = net.createServer((socket) => {
  let pending = Buffer.alloc(0);
  socket.on("data", (chunk) => {
    pending = Buffer.concat([pending, chunk]);
    while (pending.length >= 7) {
      const frameLength = 6 + pending.readUInt16BE(4);
      if (frameLength < 8 || frameLength > 260) { socket.destroy(new Error("无效 Modbus TCP 帧长度")); return; }
      if (pending.length < frameLength) return;
      const frame = pending.subarray(0, frameLength);
      pending = pending.subarray(frameLength);
      socket.write(respond(frame));
    }
  });
  socket.on("error", () => undefined);
});

server.listen(port, host, () => {
  process.stdout.write(`GuanSeq Modbus 仿真端点已启动：${host}:${port}，unitId=${unitId}\n`);
  process.stdout.write("仅用于开发/测试；仿真通过不代表真实设备或现场验收通过。\n");
});

function shutdown() { server.close(() => process.exit(0)); }
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
