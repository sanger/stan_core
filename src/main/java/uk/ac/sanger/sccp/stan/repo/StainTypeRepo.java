package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.StainType;

import java.util.List;
import java.util.Optional;

public interface StainTypeRepo extends CrudRepository<StainType, Integer> {
    List<StainType> findAllByEnabled(boolean enabled);

    Optional<StainType> findByName(String name);
}
