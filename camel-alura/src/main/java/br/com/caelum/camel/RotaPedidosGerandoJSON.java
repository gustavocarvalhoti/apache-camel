package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidosGerandoJSON {

    public static void main(String[] args) throws Exception {

        // OBS: Apontar o run para /home/gustavo/dev-tools/projects/apache-camel/camel-alura
        // Ele vai gerar na pasta  /home/gustavo/dev-tools/projects/apache-camel/camel-alura/saida

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            // Vai na pasta pedidos a cada 5 segundos e verifica os dados
            @Override
            public void configure() throws Exception {

                // Colocar no inicio
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

                // Grava em arquivo nesse projeto, no formato JSON
                from("file:pedidos?delay=5s&noop=true") // noop=true - não remove os arquivos da pasta pedidos
                        .split().xpath("/pedido/itens/item") // Separa a lista de itens do pedido, divide a mensagem
                        .filter().xpath("/item/formato[text()='EBOOK']") // Pega somente o EBOOK
                        .log("${id}")
                        .marshal().xmljson() // Convert to XML
                        .log("${body}")
                        .setHeader("CamelFileName", simple("${file:name.noext}.json"))
                        .to("file:saida"); // Salva na pasta saida
            }
        });
        context.start();
        Thread.sleep(20000);
        context.stop();
    }
}