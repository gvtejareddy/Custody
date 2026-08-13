const { ServiceBusAdministrationClient } = require("@azure/service-bus");

const connectionString = process.env.AZURE_SERVICE_BUS_CONNECTION_STRING;
const queueName = process.env.OUTBOX_QUEUE_NAME || 'outbox-events';

if (!connectionString) {
  console.error('AZURE_SERVICE_BUS_CONNECTION_STRING is not set');
  process.exit(2);
}

async function ensureQueue() {
  const adminClient = new ServiceBusAdministrationClient(connectionString);
  try {
    const exists = await adminClient.getQueueRuntimeProperties(queueName).then(() => true).catch(() => false);
    if (exists) {
      console.log(`Queue '${queueName}' already exists`);
      return;
    }
    console.log(`Creating queue '${queueName}'`);
    await adminClient.createQueue(queueName);
    console.log('Queue created');
  } catch (err) {
    console.error('Failed to create queue:', err.message || err);
    process.exit(1);
  }
}

ensureQueue().then(() => process.exit(0));
