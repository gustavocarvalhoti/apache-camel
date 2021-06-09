package br.com.caelum.camel.jms;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * No caso de assincrono, tem request e response
 */
public class RotaJmsInOut {

    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("activemq:queue:pedidos.req").
                        log("${body}").
                        setHeader(Exchange.FILE_NAME, constant("mensagem.txt")).
                        to("file:saida");
            }
        });

        context.start();
        Thread.sleep(20000);
        context.stop();
    }
}
