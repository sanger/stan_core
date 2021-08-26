package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityManager;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link OperationService}
 * @author dr6
 */
public class OperationServiceTest {
    private EntityManager mockEntityManager;
    private OperationRepo mockOpRepo;
    private ActionRepo mockActionRepo;
    private OperationService opService;

    private List<Operation> savedOps;
    private List<Action> savedActions;
    private int idCounter = 1000;

    @BeforeEach
    void setup() {
        mockEntityManager = mock(EntityManager.class);
        mockOpRepo = mock(OperationRepo.class);
        mockActionRepo = mock(ActionRepo.class);
        mockOpSave();
        mockActionSaveAll();
        mockRefresh();
        opService = new OperationService(mockEntityManager, mockOpRepo, mockActionRepo);
        savedActions = new ArrayList<>();
        savedOps = new ArrayList<>();
    }

    private void mockOpSave() {
        when(mockOpRepo.save(any())).then(invocationOnMock -> {
            Operation op = invocationOnMock.getArgument(0);
            assertNull(op.getId());
            op.setId(++idCounter);
            savedOps.add(op);
            return op;
        });
    }

    private void mockActionSaveAll() {
        when(mockActionRepo.saveAll(any())).then(invocationOnMock -> {
            Iterable<Action> actions = invocationOnMock.getArgument(0);
            for (Action action : actions) {
                assertNull(action.getId());
                action.setId(++idCounter);
                savedActions.add(action);
            }
            return actions;
        });
    }

    private void mockRefresh() {
        doAnswer(invocationOnMock -> {
            Operation op = invocationOnMock.getArgument(0);
            final int opId = op.getId();
            List<Action> actions = savedActions.stream()
                    .filter(ac -> ac.getOperationId()==opId)
                    .collect(toList());
            op.setActions(actions);
            return null;
        }).when(mockEntityManager).refresh(any(Operation.class));
    }

    @Test
    public void testCreateOperationWithNoActions() {
        OperationType opType = new OperationType(1, "Passage");
        User user = EntityFactory.getUser();
        assertThrows(IllegalArgumentException.class, () -> opService.createOperation(opType, user, List.of(), null));
        assertThat(savedOps).isEmpty();
        assertThat(savedActions).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testCreateOperationFromActions(boolean withMutator) {
        OperationType opType = new OperationType(1, "Passage");
        User user = EntityFactory.getUser();
        Slot slot0 = EntityFactory.getTube().getFirstSlot();
        Sample sample = slot0.getSamples().get(0);
        Slot slot1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()).getFirstSlot();
        Slot slot2 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()).getFirstSlot();
        List<Action> actions = Arrays.asList(
                new Action(null, null, slot0, slot2, sample, sample),
                new Action(null, null, slot1, slot2, sample, sample)
        );
        Operation op;
        if (withMutator) {
            List<Operation> opStash = new ArrayList<>(1);
            op = opService.createOperation(opType, user, actions, null, opStash::add);
            assertEquals(opStash.size(), 1);
        } else {
            op = opService.createOperation(opType, user, actions, null);
        }

        assertNotNull(op.getId());
        assertThat(savedOps).contains(op);
        assertThat(savedActions).hasSameElementsAs(actions);
        assertEquals(op.getActions().size(), actions.size());
        for (Action action : op.getActions()) {
            assertNotNull(action.getId());
        }
        verify(mockEntityManager).refresh(op);
    }

    @Test
    public void testCreateOperationFromSlots() {
        OperationType opType = new OperationType(1, "Passage");
        User user = EntityFactory.getUser();
        Slot slot0 = EntityFactory.getTube().getFirstSlot();
        Sample sample = slot0.getSamples().get(0);

        Operation op = opService.createOperationInPlace(opType, user, slot0, sample);
        assertNotNull(op.getId());
        assertThat(savedOps).contains(op);
        assertThat(savedActions).hasSize(1);
        Action action = savedActions.get(0);
        assertEquals(action.getSource(), slot0);
        assertEquals(action.getDestination(), slot0);
        assertEquals(action.getSample(), sample);
        assertEquals(action.getOperationId(), op.getId());
        assertNotNull(action.getId());

        assertThat(op.getActions()).isEqualTo(savedActions);
        verify(mockEntityManager).refresh(op);
    }
}
