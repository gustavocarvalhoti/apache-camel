package br.com.caelum.camel.jms;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class EnviarPedidos {

    /**
     * Vai na pasta /home/gustavo/dev-tools/projects/apache-camel/camel-alura/pedidos
     * e envia para a fila JMS
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        CamelContext context = new DefaultCamelContext();
        context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://127.0.0.1:61616"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("file:pedidos?delay=5s&noop=true").
                        to("activemq:queue:pedidos");

            }
        });

        context.start();
        Thread.sleep(10000);
        context.stop();
    }
}