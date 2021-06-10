package br.com.caelum.camel.jms;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidosJMS {

    public static void main(String[] args) throws Exception {

        CamelContext context = new DefaultCamelContext();
        // 61616 - Porta default
        context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://127.0.0.1:61616"));

        context.addRoutes(new RouteBuilder() {
            // Vai na pasta pedidos a cada 5 segundos e verifica os dados
            @Override
            public void configure() throws Exception {

                // Escreve ns fila pedidos.DLQ caso de erro
                errorHandler(
                        deadLetterChannel("activemq:queue:pedidos.DLQ")
                                .logExhaustedMessageHistory(true) // Loga o erro
                                .maximumRedeliveries(3)  // Tenta 3 vezes
                                .onRedelivery(new Processor() {
                                    @Override
                                    public void process(Exchange exchange) throws Exception {
                                        int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
                                        int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
                                        System.out.println("Redelivery:. " + counter + "/" + max);
                                    }
                                })
                                .redeliveryDelay(2000) // intervalo
                );

                // Lê da fila activemq:queue:pedidos e envia os pedidos
                // http://127.0.0.1:8161/admin/queues.jsp
                from("activemq:queue:pedidos")
                        .routeId("rota-pedidos")
                        .to("validator:pedido.xsd") // Está em resources, ele valida a estrutura
                        .multicast()
                        .to("direct:http")
                        .to("direct:soap");

                // Ele leu acima os pedidos e agora está despachando
                // Faz o get na URL http4://localhost:8080/webservices/ebook/item passando os parametros
                from("direct:http").routeId("rota-http")
                        .log("Request HTTP *************************************")
                        .setProperty("pedidoId", xpath("/pedido/id/text()")) // Cria uma propriedade
                        .setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()"))
                        .split().xpath("/pedido/itens/item") // Separa a lista de itens do pedido, divide a mensagem
                        .filter().xpath("/item/formato[text()='EBOOK']") // Pega somente o EBOOK
                        .setProperty("ebookId", xpath("/item/livro/codigo/text()"))
                        .log("HTTP: ${id}")
                        .log("BODY: \n${body}")
                        .marshal().xmljson() // Convert to XML
                        .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                        .setHeader(Exchange.HTTP_QUERY,
                                simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}&")
                        )
                        .to("http4://localhost:8080/webservices/ebook/item");

                // Faz o post para a URL SOAP
                from("direct:soap").routeId("rota-soap")
                        .log("Request SOAP *************************************")
                        .to("xslt:pedido-para-soap.xslt") // Template em resources
                        .log("SOAP: ${id}")
                        .log("BODY: \n${body}")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
                        .to("http4://localhost:8080/webservices/financeiro");
            }
        });
        context.start();
        Thread.sleep(20000);
        context.stop();
    }
}