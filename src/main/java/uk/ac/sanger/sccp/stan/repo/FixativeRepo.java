package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Fixative;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface FixativeRepo extends CrudRepository<Fixative, Integer> {
    Optional<Fixative> findByName(String name);

    default Fixative getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Fixative not found: "+repr(name)));
    }

    List<Fixative> findAllByNameIn(Collection<String> names);

    List<Fixative> findAllByEnabled(boolean enabled);
}
