package cl.duoc.autocare.config;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile("catalogo-local")
public class MongoTransaccionesConfig {

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory fabrica) {
        TransactionOptions opciones = TransactionOptions.builder()
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.SNAPSHOT)
                .writeConcern(WriteConcern.MAJORITY)
                .build();
        return new MongoTransactionManager(fabrica, opciones);
    }
}
