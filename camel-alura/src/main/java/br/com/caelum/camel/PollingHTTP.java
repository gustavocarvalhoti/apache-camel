package br.com.caelum.camel;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.thoughtworks.xstream.XStream;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

import java.text.SimpleDateFormat;

/**
 * Bate na URL: http4://argentumws-spring.herokuapp.com/negociacoes
 * Grava em: /home/gustavo/dev-tools/projects/apache-camel/saida
 */
public class PollingHTTP {

    public static void main(String[] args) throws Exception {

        SimpleRegistry registro = new SimpleRegistry();
        registro.put("mysql", criaDataSource());
        CamelContext context = new DefaultCamelContext(registro); // Construtor recebe registro

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final String SQL = "insert into negociacao(preco, quantidade, data) values (${property.preco}, ${property.quantidade}, '${property.data}')";

                final XStream xStream = new XStream();
                xStream.alias("negociacao", Negociacao.class);

                from("timer://negociacoes?fixedRate=true&delay=3s&period=360s")
                        .to("http4://argentumws.caelum.com.br/negociacoes")
                        .split().xpath("/list/negociacao")
                        .convertBodyTo(String.class)
                        .unmarshal(new XStreamDataFormat(xStream))
                        .split(body())
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Negociacao negociacao = exchange.getIn().getBody(Negociacao.class);
                                exchange.setProperty("preco", negociacao.getPreco());
                                exchange.setProperty("quantidade", negociacao.getQuantidade());
                                String data = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(negociacao.getData().getTime());
                                exchange.setProperty("data", data);
                            }
                        })
                        .log(simple(SQL).toString())
                        .setBody(simple(SQL))
                        .delay(1000);
                        //.to("jdbc:mysql");
                        // Deveria fazer o insert, mas não consegi :(
            }
        });

        context.start();
        Thread.sleep(20000);
        context.stop();
    }

    private static MysqlConnectionPoolDataSource criaDataSource() {
        MysqlConnectionPoolDataSource mysqlDs = new MysqlConnectionPoolDataSource();
        mysqlDs.setDatabaseName("camel");
        mysqlDs.setServerName("127.0.0.1");
        mysqlDs.setPort(3306);
        mysqlDs.setUser("root");
        mysqlDs.setPassword("root");
        return mysqlDs;
    }
}