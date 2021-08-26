package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link MeasurementRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestMeasurementRepo {
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    MeasurementRepo measurementRepo;
    @Autowired
    OperationRepo opRepo;

    @Transactional
    @Test
    public void testFindAllBySlotIdIn() {
        OperationType opType = entityCreator.createOpType("DoStuff", null);
        User user = entityCreator.createUser("user1");
        Operation op = opRepo.save(new Operation(null, opType, null, List.of(), user));
        Integer opId = op.getId();

        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample = entityCreator.createSample(tissue, null);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 3);
        Labware lw = entityCreator.createLabware("STAN-001", lt, sample, sample);
        Integer slot1id = lw.getFirstSlot().getId();
        Integer slot2id = lw.getSlots().get(1).getId();
        Integer slot3id = lw.getSlots().get(2).getId();
        Integer sampleId = sample.getId();
        Measurement m1 = measurementRepo.save(new Measurement(null, "Thickness", "5.2", sampleId, opId, slot1id));
        Measurement m2 = measurementRepo.save(new Measurement(null, "Thickness", "6.7", sampleId, opId, slot2id));
        Measurement m3 = measurementRepo.save(new Measurement(null, "Bananas", "Blue", sampleId, opId, slot1id));

        assertThat(measurementRepo.findAllBySlotIdIn(List.of(slot3id))).isEmpty();
        assertThat(measurementRepo.findAllBySlotIdIn(List.of(slot2id, slot3id))).containsOnly(m2);
        assertThat(measurementRepo.findAllBySlotIdIn(List.of(slot1id, slot2id, slot3id))).containsOnly(m1, m2, m3);
    }

    @Transactional
    @Test
    public void testFindAllByOperationIdIn() {
        OperationType opType = entityCreator.createOpType("DoStuff", null);
        User user = entityCreator.createUser("user1");
        int op1id = opRepo.save(new Operation(null, opType, null, List.of(), user)).getId();
        int op2id = opRepo.save(new Operation(null, opType, null, List.of(), user)).getId();
        Measurement m1 = measurementRepo.save(new Measurement(null, "Blueing", "14", null, op1id, null));
        Measurement m2 = measurementRepo.save(new Measurement(null, "Greening", "200", null, op2id, null));
        assertThat(measurementRepo.findAllByOperationIdIn(List.of(op1id, op2id))).containsExactlyInAnyOrder(m1, m2);
        assertThat(measurementRepo.findAllByOperationIdIn(List.of(op1id))).containsExactly(m1);
        assertThat(measurementRepo.findAllByOperationIdIn(List.of(-400))).isEmpty();
    }
}
