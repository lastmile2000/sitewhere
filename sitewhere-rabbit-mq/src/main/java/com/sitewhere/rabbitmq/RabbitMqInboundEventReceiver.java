/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.rabbitmq;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.sitewhere.server.lifecycle.TenantLifecycleComponent;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.device.communication.IInboundEventReceiver;
import com.sitewhere.spi.device.communication.IInboundEventSource;
import com.sitewhere.spi.server.lifecycle.LifecycleComponentType;

/**
 * Binary inbound event source that consumes messages from a RabbitMQ broker.
 * 
 * @author Derek
 */
public class RabbitMqInboundEventReceiver extends TenantLifecycleComponent implements
		IInboundEventReceiver<byte[]> {

	/** Static logger instance */
	private static Logger LOGGER = Logger.getLogger(RabbitMqInboundEventReceiver.class);

	/** Default connection URI */
	private static final String DEFAULT_CONNECTION_URI = "amqp://localhost";

	/** Default queue name */
	private static final String DEFAULT_QUEUE_NAME = "sitewhere.input";

	/** Default number of consumers if not specified */
	private static final int DEFAULT_NUM_CONSUMERS = 5;

	/** Parent event source */
	private IInboundEventSource<byte[]> eventSource;

	/** Connection URI */
	private String connectionUri = DEFAULT_CONNECTION_URI;

	/** Queue name */
	private String queueName = DEFAULT_QUEUE_NAME;

	/** Number of consumers to use */
	private int numConsumers = DEFAULT_NUM_CONSUMERS;

	/** RabbitMQ connection */
	private Connection connection;

	/** RabbitMQ channel */
	private Channel channel;

	/** Used for consumer thread pool */
	private ExecutorService executors;

	public RabbitMqInboundEventReceiver() {
		super(LifecycleComponentType.InboundEventReceiver);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#start()
	 */
	@Override
	public void start() throws SiteWhereException {
		executors = Executors.newFixedThreadPool(getNumConsumers());
		try {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setUri(getConnectionUri());
			this.connection = factory.newConnection(executors);
			this.channel = connection.createChannel();

			LOGGER.info("RabbitMQ receiver connected to: " + getConnectionUri());

			channel.queueDeclare(getQueueName(), false, false, false, null);

			LOGGER.info("RabbitMQ receiver using queue: " + getQueueName());

			// Add consumer callback for channel.
			Consumer consumer = new DefaultConsumer(channel) {

				@Override
				public void handleDelivery(String consumerTag, Envelope envelope,
						AMQP.BasicProperties properties, byte[] body) throws IOException {
					onEventPayloadReceived(body);
				}
			};
			channel.basicConsume(getQueueName(), true, consumer);
		} catch (Exception e) {
			throw new SiteWhereException("Unable to start RabbitMQ event receiver.", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#stop()
	 */
	@Override
	public void stop() throws SiteWhereException {
		try {
			if (channel != null) {
				channel.close();
			}
			if (connection != null) {
				connection.close();
			}
		} catch (Exception e) {
			throw new SiteWhereException("Error stopping RabbitMQ event receiver.", e);
		}
		executors.shutdownNow();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#getLogger()
	 */
	@Override
	public Logger getLogger() {
		return LOGGER;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.device.communication.IInboundEventReceiver#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return "RabbitMQ uri=" + getConnectionUri() + " queue=" + getQueueName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sitewhere.spi.device.communication.IInboundEventReceiver#onEventPayloadReceived
	 * (java.lang.Object)
	 */
	@Override
	public void onEventPayloadReceived(byte[] payload) {
		getEventSource().onEncodedEventReceived(this, payload);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sitewhere.spi.device.communication.IInboundEventReceiver#setEventSource(com
	 * .sitewhere.spi.device.communication.IInboundEventSource)
	 */
	@Override
	public void setEventSource(IInboundEventSource<byte[]> source) {
		this.eventSource = source;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.device.communication.IInboundEventReceiver#getEventSource()
	 */
	@Override
	public IInboundEventSource<byte[]> getEventSource() {
		return eventSource;
	}

	public String getConnectionUri() {
		return connectionUri;
	}

	public void setConnectionUri(String connectionUri) {
		this.connectionUri = connectionUri;
	}

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public int getNumConsumers() {
		return numConsumers;
	}

	public void setNumConsumers(int numConsumers) {
		this.numConsumers = numConsumers;
	}
}