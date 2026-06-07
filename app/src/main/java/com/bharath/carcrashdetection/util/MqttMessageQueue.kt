package com.bharath.carcrashdetection.util

import java.util.concurrent.ConcurrentLinkedQueue

object MqttMessageQueue {
    data class QueuedMessage(
        val topic: String,
        val payload: String,
        val qos: Int,
        val retained: Boolean
    )
    
    private val queue = ConcurrentLinkedQueue<QueuedMessage>()

    fun enqueue(topic: String, payload: String, qos: Int = 1, retained: Boolean = false) {
        queue.add(QueuedMessage(topic, payload, qos, retained))
    }

    fun dequeue(): QueuedMessage? = queue.poll()

    fun isEmpty(): Boolean = queue.isEmpty()

    fun retryAll(publishFunc: (String, String, Int, Boolean) -> Boolean) {
        val failed = mutableListOf<QueuedMessage>()
        while (queue.isNotEmpty()) {
            val item = queue.poll()
            if (item != null) {
                val success = publishFunc(item.topic, item.payload, item.qos, item.retained)
                if (!success) {
                    failed.add(item)
                }
            }
        }
        // Re-enqueue failed messages
        failed.forEach { queue.add(it) }
    }
}