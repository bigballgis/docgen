const { Kafka, logLevel } = require('kafkajs');
const { v4: uuidv4 } = require('uuid');
const documentService = require('./documentService');
const config = require('../config');
const logger = require('../utils/logger');

/**
 * Kafka 异步文档生成服务
 * Kafka 不可用时自动降级为同步模式
 */

let producer = null;
let consumer = null;
let isConnected = false;

// ========== 任务状态管理（内存 Map，Redis 不可用时的降级方案） ==========
const taskStatusMap = new Map();

/**
 * 更新任务状态
 * @param {string} taskId - 任务 ID
 * @param {string} status - 状态：pending/processing/completed/failed
 * @param {Object|null} [result=null] - 结果数据
 */
function updateTaskStatus(taskId, status, result = null) {
  taskStatusMap.set(taskId, {
    status,
    result,
    updatedAt: new Date().toISOString(),
  });
}

/**
 * 获取任务状态
 * @param {string} taskId - 任务 ID
 * @returns {Object} 任务状态
 */
function getTaskStatus(taskId) {
  return taskStatusMap.get(taskId) || { status: 'not_found' };
}

/**
 * 初始化 Kafka 连接
 * @param {string[]} brokers - Kafka broker 地址列表
 * @returns {Promise<boolean>} 是否连接成功
 */
async function initKafka(brokers) {
  try {
    const kafka = new Kafka({
      clientId: config.kafka.clientId,
      brokers: brokers,
      logLevel: logLevel.WARN,
    });

    producer = kafka.producer();
    await producer.connect();

    consumer = kafka.consumer({ groupId: 'docgen-consumer-group' });
    await consumer.connect();
    await consumer.subscribe({ topic: 'document-generation', fromBeginning: false });

    // 启动消费者
    await consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        try {
          const task = JSON.parse(message.value.toString());
          logger.info('Kafka 收到文档生成任务', { taskId: task.taskId });
          await processDocumentTask(task);
        } catch (err) {
          logger.error('Kafka 消息处理失败', { error: err.message });
        }
      },
    });

    isConnected = true;
    logger.info('Kafka 连接成功', { brokers });
    return true;
  } catch (err) {
    isConnected = false;
    producer = null;
    consumer = null;
    logger.warn('Kafka 连接失败，降级为同步模式', { error: err.message });
    return false;
  }
}

/**
 * 发送文档生成任务到 Kafka
 * @param {Object} task - 任务对象
 * @param {string} task.taskId - 任务 ID
 * @param {number} task.templateId - 模板 ID
 * @param {Object} task.data - 填充数据
 * @param {string} task.outputFormat - 输出格式
 * @param {number} task.userId - 用户 ID
 * @param {string} task.tenantId - 租户 ID
 * @returns {Promise<Object>} 任务提交结果
 */
async function sendDocumentTask(task) {
  if (!isConnected || !producer) {
    // 降级：同步执行
    logger.info('Kafka 不可用，同步生成文档', { taskId: task.taskId });
    const result = await documentService.generateDocument(
      task.templateId,
      task.data,
      task.outputFormat,
      task.userId,
      task.tenantId
    );
    updateTaskStatus(task.taskId, 'completed', {
      fileName: result.fileName,
      filePath: result.filePath,
    });
    return { taskId: task.taskId, status: 'completed', result };
  }

  try {
    await producer.send({
      topic: 'document-generation',
      messages: [
        {
          key: Buffer.from(task.taskId),
          value: Buffer.from(JSON.stringify(task)),
        },
      ],
    });

    updateTaskStatus(task.taskId, 'pending');
    logger.info('文档生成任务已发送到 Kafka', { taskId: task.taskId });
    return { taskId: task.taskId, status: 'pending' };
  } catch (err) {
    // 发送失败，降级为同步
    logger.warn('Kafka 发送失败，降级为同步生成', { error: err.message });
    const result = await documentService.generateDocument(
      task.templateId,
      task.data,
      task.outputFormat,
      task.userId,
      task.tenantId
    );
    updateTaskStatus(task.taskId, 'completed', {
      fileName: result.fileName,
      filePath: result.filePath,
    });
    return { taskId: task.taskId, status: 'completed', result };
  }
}

/**
 * 处理文档生成任务（Kafka 消费者回调）
 * @param {Object} task - 任务对象
 */
async function processDocumentTask(task) {
  try {
    // 更新任务状态为 processing
    updateTaskStatus(task.taskId, 'processing');

    // 执行文档生成
    const result = await documentService.generateDocument(
      task.templateId,
      task.data,
      task.outputFormat,
      task.userId,
      task.tenantId
    );

    // 更新任务状态为 completed
    updateTaskStatus(task.taskId, 'completed', {
      fileName: result.fileName,
      filePath: result.filePath,
    });

    logger.info('文档生成任务完成', { taskId: task.taskId });
  } catch (err) {
    // 更新任务状态为 failed
    updateTaskStatus(task.taskId, 'failed', { error: err.message });
    logger.error('文档生成任务失败', { taskId: task.taskId, error: err.message });
  }
}

/**
 * 断开 Kafka 连接
 */
async function disconnectKafka() {
  try {
    if (producer) {
      await producer.disconnect();
    }
    if (consumer) {
      await consumer.disconnect();
    }
    logger.info('Kafka 连接已断开');
  } catch (err) {
    logger.warn('Kafka 断开连接失败', { error: err.message });
  }
}

/**
 * 检查 Kafka 是否可用
 * @returns {boolean}
 */
function isKafkaConnected() {
  return isConnected;
}

/**
 * 生成任务 ID
 * @returns {string} UUID
 */
function generateTaskId() {
  return uuidv4();
}

module.exports = {
  initKafka,
  sendDocumentTask,
  processDocumentTask,
  disconnectKafka,
  isKafkaConnected,
  updateTaskStatus,
  getTaskStatus,
  generateTaskId,
};
