package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Fixative;
import uk.ac.sanger.sccp.stan.repo.FixativeRepo;

import java.util.Optional;

/**
 * Service for dealing with {@link Fixative}s
 * @author dr6
 */
@Service
public class FixativeService extends BaseAdminService<Fixative, FixativeRepo> {
    @Autowired
    public FixativeService(FixativeRepo fixativeRepo,
                           @Qualifier("fixativeNameValidator") Validator<String> fixativeNameValidator) {
        super(fixativeRepo, "Fixative", "Name", fixativeNameValidator);
    }

    @Override
    protected Fixative newEntity(String name) {
        return new Fixative(null, name);
    }

    @Override
    protected Optional<Fixative> findEntity(FixativeRepo repo, String name) {
        return repo.findByName(name);
    }
}
