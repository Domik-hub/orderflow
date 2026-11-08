package dev.domikhub.orderflow;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OrderFlowApplication {
    public static void main(String[] args) { SpringApplication.run(OrderFlowApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
