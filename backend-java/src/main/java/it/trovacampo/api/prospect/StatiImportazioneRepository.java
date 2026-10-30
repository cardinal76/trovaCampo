package it.trovacampo.api.prospect;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface StatiImportazioneRepository
        extends MongoRepository<StatoImportazioneProspect, Integer> {}
