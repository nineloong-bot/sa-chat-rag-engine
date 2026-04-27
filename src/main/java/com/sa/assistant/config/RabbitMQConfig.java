package com.sa.assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String RAG_EXCHANGE = "rag.exchange";
    public static final String DOCUMENT_QUEUE = "rag.document.queue";
    public static final String DOCUMENT_ROUTING_KEY = "rag.document";

    public static final String DLX_EXCHANGE = "rag.dlx.exchange";
    public static final String DLX_QUEUE = "rag.dlx.queue";
    public static final String DLX_ROUTING_KEY = "rag.dlx";

    /**
     * 重试次数 Header key。消费者通过此 header 追踪已重试次数。
     */
    public static final String RETRY_COUNT_HEADER = "x-retry-count";

    /**
     * 最大重试次数。超过此次数后消息进入 DLX。
     */
    public static final int MAX_RETRY_COUNT = 3;

    @Bean
    public DirectExchange ragExchange() {
        return ExchangeBuilder.directExchange(RAG_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 文档处理队列。
     *
     * 注意：未设置 x-message-ttl。
     * x-message-ttl 会使队列中等待的消息超时后直接进入 DLX，
     * 这不是"处理超时"，而是"排队超时"——极易误杀排队中的正常消息。
     * 处理超时应由消费者侧的 Tika parse 超时（DocumentParseService）来控制。
     */
    @Bean
    public Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLX_QUEUE)
                .withArgument("x-message-ttl", 7 * 24 * 60 * 60 * 1000) // DLX 中保留 7 天，供人工排查
                .build();
    }

    @Bean
    public Binding documentBinding(Queue documentQueue, DirectExchange ragExchange) {
        return BindingBuilder.bind(documentQueue).to(ragExchange).with(DOCUMENT_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned ->
                log.warn("Message returned | messageId={}, replyText={}",
                        returned.getMessage().getMessageProperties().getMessageId(),
                        returned.getReplyText()));
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("Message confirm failed | cause={}", cause);
            }
        });
        return template;
    }

    /**
     * 文档处理专用 ListenerContainerFactory。
     * prefetch=1 确保消费者每次只取一条消息，避免 OOM 时影响多条消息。
     * acknowledge-mode=MANUAL 使消费者可以精确控制 ACK/NACK 时机。
     *
     * 消费者需在 @RabbitListener 中显式指定 containerFactory = "documentListenerContainerFactory"
     */
    @Bean
    public SimpleRabbitListenerContainerFactory documentListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setPrefetchCount(1);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 默认并发消费者数：可根据 CPU 核心数调整
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }
}
