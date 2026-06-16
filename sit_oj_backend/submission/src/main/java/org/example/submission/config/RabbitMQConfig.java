package org.example.submission.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明判题请求/结果队列、交换机、绑定
 */
@Configuration
public class RabbitMQConfig {

    // ============ 队列名称 ============
    public static final String JUDGE_REQUEST_QUEUE = "judge.request.queue";
    public static final String JUDGE_RESULT_QUEUE  = "judge.result.queue";

    // 死信（判题超时或异常）
    public static final String JUDGE_DLX          = "judge.dlx";
    public static final String JUDGE_REQUEST_DLQ  = "judge.request.dlq";

    // ============ Exchange & Routing Key ============
    public static final String JUDGE_EXCHANGE             = "judge.exchange";
    public static final String JUDGE_REQUEST_ROUTING_KEY  = "judge.request";
    public static final String JUDGE_RESULT_ROUTING_KEY   = "judge.result";

    // ---- Exchange ----
    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(JUDGE_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(JUDGE_DLX);
    }

    // ---- 队列 ----
    @Bean
    public Queue judgeRequestQueue() {
        return QueueBuilder.durable(JUDGE_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", JUDGE_DLX)
                .withArgument("x-dead-letter-routing-key", JUDGE_REQUEST_ROUTING_KEY)
                .withArgument("x-message-ttl", 120_000)   // 2 分钟未消费视为超时
                .build();
    }

    @Bean
    public Queue judgeResultQueue() {
        return QueueBuilder.durable(JUDGE_RESULT_QUEUE).build();
    }

    @Bean
    public Queue judgeRequestDLQ() {
        return QueueBuilder.durable(JUDGE_REQUEST_DLQ).build();
    }

    // ---- Binding ----
    @Bean
    public Binding judgeRequestBinding() {
        return BindingBuilder.bind(judgeRequestQueue())
                .to(judgeExchange()).with(JUDGE_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding judgeResultBinding() {
        return BindingBuilder.bind(judgeResultQueue())
                .to(judgeExchange()).with(JUDGE_RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(judgeRequestDLQ())
                .to(deadLetterExchange()).with(JUDGE_REQUEST_ROUTING_KEY);
    }

    // JSON 序列化，避免 JDK 序列化
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
