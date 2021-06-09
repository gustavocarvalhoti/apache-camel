package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

    public static void main(String[] args) throws Exception {

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            // Vai na pasta pedidos a cada 5 segundos e verifica os dados
            @Override
            public void configure() throws Exception {

                // Colocar no inicio, validação de mensagens
                errorHandler(
                        deadLetterChannel("file:erro")
                                .logExhaustedMessageHistory(true) // Loga o erro
                                .maximumRedeliveries(3)  // tenta 3 vezes
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

                // Envia a mesma mensagem para os 2 serviços, http e soap
                from("file:pedidos?delay=5s&noop=true")
                        .routeId("rota-pedidos")
                        .to("validator:pedido.xsd") // Está em resources, ele valida a estrutura
                        .multicast()
                        .to("direct:http")
                        .to("direct:soap");

                // Faz o post para a URL HTTP
                from("direct:http").routeId("rota-http")
                        //.log("${body}")
                        .setProperty("pedidoId", xpath("/pedido/id/text()")) // Cria uma propriedade
                        .setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()"))
                        .split().xpath("/pedido/itens/item") // Separa a lista de itens do pedido, divide a mensagem
                        .filter().xpath("/item/formato[text()='EBOOK']") // Pega somente o EBOOK
                        .setProperty("ebookId", xpath("/item/livro/codigo/text()"))
                        //.log("HTTP: ${id}")
                        .marshal().xmljson() // Convert to XML
                        //.log("${body}")
                        .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                        .setHeader(Exchange.HTTP_QUERY,
                                simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}&")
                        )
                        .to("http4://localhost:8080/webservices/ebook/item");

                // Faz o post para a URL SOAP
                from("direct:soap").routeId("rota-soap")
                        .to("xslt:pedido-para-soap.xslt") // Template em resources
                        .log("SOAP: ${id}")
                        .log("BODY: ${body}")
                        //.setBody(constant("<envelop>teste</envelope>")) // Force set body
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
                        .to("http4://localhost:8080/webservices/financeiro");
            }
        });
        context.start();
        Thread.sleep(20000);
        context.stop();
    }

    /*
    Pedido pedido = new Pedido();

    //para gravar o XML no arquivo
    JAXB.marshal(pedido, new FileOutputStream("pedido.xml"));

    //para criar o objeto a partir de XML
    Pedido pedidoDoXml = JAXB.unmarshal(new FileInputStream("pedido.xml"), Pedido.class);

    //para processar em paralelo
    from("file:pedidos?delay=5s&noop=true").
    multicast().
        parallelProcessing().
            timeout(500). //millis
                to("direct:soap").
                to("direct:http");

    // SEDA
    A ideia do SEDA é que cada rota (e sub-rota) possua uma fila dedicada de entrada
    e as rotas enviam mensagens para essas filas para se comunicar.
    Dentro dessa arquitetura, as mensagens são chamadas de eventos.
    A rota fica então consumindo as mensagens/eventos da fila, tudo funcionando em paralelo.

    Para usar SEDA basta substituir a palavra direct por seda, com isso, o multicast se tornará desnecessário:

    from("file:pedidos?delay=5s&noop=true").
    routeId("rota-pedidos").
    to("seda:soap").
    to("seda:http");

    from("seda:soap").
    routeId("rota-soap").
    log("chamando servico soap ${body}").
    to("mock:soap");

    from("seda:http").
    routeId("rota-http").
    setProperty("pedidoId", xpath("/pedido/id/text()")).
    setProperty("email", xpath("/pedido/pagamento/email-titular/text()")).
    split().
    xpath("/pedido/itens/item").
    filter().
    xpath("/item/formato[text()='EBOOK']").
    setProperty("ebookId", xpath("/item/livro/codigo/text()")).
    setHeader(Exchange.HTTP_QUERY,
    simple("clienteId=${property.email}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}")).
    to("http4://localhost:8080/webservices/ebook/item")
    */
}