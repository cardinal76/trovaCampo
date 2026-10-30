package it.trovacampo.api.indirizzi;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImpiantiOpenDataRepository extends MongoRepository<ImpiantoOpenData, String> {

    long countByFonte(String fonte);
}
