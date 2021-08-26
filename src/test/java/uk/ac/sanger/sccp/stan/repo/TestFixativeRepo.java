package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Fixative;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link FixativeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestFixativeRepo {
    @Autowired
    private FixativeRepo fixativeRepo;

    @Test
    @Transactional
    public void testGetByName() {
        assertThrows(EntityNotFoundException.class, () -> fixativeRepo.getByName("Bananas"));
        Fixative pr = fixativeRepo.save(new Fixative(null, "Bananas"));
        assertEquals(pr, fixativeRepo.getByName("Bananas"));
    }

    @Test
    @Transactional
    public void findAllByEnabled() {
        List<Fixative> fixatives = fixativeRepo.findAllByEnabled(true);

        Fixative pr = fixativeRepo.save(new Fixative(null, "Bananas"));
        List<Fixative> moar = fixativeRepo.findAllByEnabled(true);
        assertThat(moar).contains(pr).hasSize(fixatives.size()+1);

        pr.setEnabled(false);
        fixativeRepo.save(pr);
        List<Fixative> less = fixativeRepo.findAllByEnabled(true);
        assertThat(less).hasSize(fixatives.size()).containsExactlyInAnyOrderElementsOf(fixatives);

        List<Fixative> disabled = fixativeRepo.findAllByEnabled(false);
        assertThat(disabled).contains(pr);
    }
}
