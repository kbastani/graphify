package org.neo4j.nlp.graph;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;

/**
 * Distributed graph concurrency service. This class handles concurrent writes
 * to Neo4j. Since computation must be distributed, this service handles
 * scaling concurrent writes to Neo4j. The design pattern for this class
 * is a distributed worker queue. Multiple workers push and pull to this
 * queue for writing to Neo4j.
 */
public class ConcurrentGraphService {

    private static final String QUEUE_NAME = "patterns";
    private static final String RABBITMQ_HOST_NAME = "localhost";
    private static final ConnectionFactory connectionFactory = new ConnectionFactory();

    private Connection connection;
    private Channel channel;

    public ConcurrentGraphService()
    {

    }

    public void sendMessage(String message) throws IOException {
        this.openConnection();
        channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
        System.out.println(" [x] Sent '" + message + "'");
    }

    public String receiveMessage() throws IOException, InterruptedException {
        this.openConnection();
        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(QUEUE_NAME, true, consumer);
        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        String message = new String(delivery.getBody());
        System.out.println(" [x] Received '" + message + "'");
        closeConnection();
        return message;
    }

    private void closeConnection() throws IOException {
        channel.close();
        connection.close();
    }

    private void openConnection() throws IOException {
        this.connection = connectionFactory.newConnection();
        connectionFactory.setHost(RABBITMQ_HOST_NAME);
        this.channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
    }
}
