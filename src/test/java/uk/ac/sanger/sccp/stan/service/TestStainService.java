package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.StainRequest;
import uk.ac.sanger.sccp.stan.request.stain.TimeMeasurement;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;

/**
 * Tests {@link StainServiceImp}
 * @author dr6
 */
public class TestStainService {
    private StainTypeRepo mockStainTypeRepo;
    private LabwareRepo mockLabwareRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private MeasurementRepo mockMeasurementRepo;
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private WorkService mockWorkService;
    private OperationService mockOpService;

    private StainServiceImp service;

    @BeforeEach
    void setup() {
        mockStainTypeRepo = mock(StainTypeRepo.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);

        service = spy(new StainServiceImp(mockStainTypeRepo, mockLabwareRepo, mockOpTypeRepo, mockMeasurementRepo,
                mockLabwareValidatorFactory, mockWorkService, mockOpService));
    }

    @Test
    public void testGetEnabledStainTypes() {
        List<StainType> stainTypes = List.of(new StainType(1, "Bananas"));
        when(mockStainTypeRepo.findAllByEnabled(true)).thenReturn(stainTypes);
        assertSame(stainTypes, service.getEnabledStainTypes());
        verify(mockStainTypeRepo).findAllByEnabled(true);
    }

    @ParameterizedTest
    @MethodSource("validateLabwareArgs")
    public void testValidateLabware(List<String> barcodes, String expectedProblem) {
        LabwareValidator mockValidator = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(mockValidator);
        List<String> problems = new ArrayList<>();
        if (barcodes==null || barcodes.isEmpty()) {
            assertThat(service.validateLabware(problems, barcodes)).isEmpty();
            verifyNoInteractions(mockValidator);
            assertThat(problems).containsExactly(expectedProblem);
            return;
        }
        List<String> expectedProblems = (expectedProblem==null ? List.of() : List.of(expectedProblem));
        when(mockValidator.getErrors()).thenReturn(expectedProblems);
        List<Labware> lw = List.of(EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()));
        when(mockValidator.getLabware()).thenReturn(lw);

        assertEquals(lw, service.validateLabware(problems, barcodes));
        assertThat(problems).containsExactlyElementsOf(expectedProblems);

        verify(mockValidator).setUniqueRequired(true);
        verify(mockValidator).loadLabware(mockLabwareRepo, barcodes);
        verify(mockValidator).validateSources();
    }

    static Stream<Arguments> validateLabwareArgs() {
        return Stream.of(
                Arguments.of(null, "No barcodes supplied."),
                Arguments.of(List.of(), "No barcodes supplied."),
                Arguments.of(List.of("STAN-400"), "Some validation problem."),
                Arguments.of(List.of("STAN-500"), null)
        );
    }

    @ParameterizedTest
    @MethodSource("validateStainTypeArgs")
    public void testValidateStainType(Object arg, String expectedProblem) {
        String name;
        StainType st;
        if (arg instanceof StainType) {
            st = (StainType) arg;
            name = st.getName();
        } else {
            name = (String) arg;
            st = null;
        }
        if (name!=null && !name.isEmpty()) {
            when(mockStainTypeRepo.findByName(name)).thenReturn(Optional.ofNullable(st));
        }
        List<String> problems = new ArrayList<>();
        assertSame(st, service.validateStainType(problems, name));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    static Stream<Arguments> validateStainTypeArgs() {
        return Stream.of(
                Arguments.of(null, "No stain type specified."),
                Arguments.of("", "No stain type specified."),
                Arguments.of("Bananas", "Stain type not found: \"Bananas\""),
                Arguments.of(new StainType(1, "H&E"), null)
        );
    }

    @ParameterizedTest
    @MethodSource("validateMeasurementsArgs")
    public void testValidateMeasurements(StainType stainType, Collection<TimeMeasurement> timeMeasurements,
                                         List<TimeMeasurement> expectedResult,
                                         Object expectedProblemArg) {
        List<String> problems = new ArrayList<>();
        List<TimeMeasurement> result = service.validateMeasurements(problems, stainType, timeMeasurements);
        assertThat(result).containsExactlyInAnyOrderElementsOf(coalesce(expectedResult, timeMeasurements));
        List<String> expectedProblems = argToList(expectedProblemArg);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static List<TimeMeasurement> makeTimeMeasurements(int blueing, int haema, int eosin) {
        return List.of(
                new TimeMeasurement("Blueing", blueing),
                new TimeMeasurement("Haematoxylin", haema),
                new TimeMeasurement("Eosin", eosin)
        );
    }

    static Stream<Arguments> validateMeasurementsArgs() {
        StainType st0 = new StainType(1, "Bananas");
        StainType st = new StainType(2, "H&E");
        List<TimeMeasurement> correctTimeMeasurements = makeTimeMeasurements(3, 40, 500);
        List<TimeMeasurement> ucTimeMeasurements = correctTimeMeasurements.stream()
                .map(tm -> new TimeMeasurement(tm.getName().toUpperCase(), tm.getSeconds()))
                .collect(toList());
        List<TimeMeasurement> withoutBlueing = correctTimeMeasurements.stream()
                .filter(tm -> !tm.getName().equalsIgnoreCase("Blueing"))
                .collect(toList());
        List<TimeMeasurement> withRepeat = Stream.concat(Stream.of(new TimeMeasurement("BLUEING", 400)), correctTimeMeasurements.stream())
                .collect(toList());
        List<TimeMeasurement> withCustard = Stream.concat(Stream.of(new TimeMeasurement("Custard", 400)), correctTimeMeasurements.stream())
                .collect(toList());
        return Arrays.stream(new Object[][] {
                {null, null, List.of(), null},
                {st, List.of(), List.of(), "No measurements supplied for stain."},
                {st0, List.of(), List.of(), null},
                {st0, List.of(new TimeMeasurement("Blueing", 500)), List.of(), "Measurements are not expected for stain type Bananas."},
                {st, makeTimeMeasurements(200, 0, 300), null, "Time measurements must be greater than zero."},
                {st, makeTimeMeasurements(-15, 7, 300), null, "Time measurements must be greater than zero."},
                {st, withoutBlueing, null, "Missing measurements: [Blueing]"},
                {st, withRepeat, correctTimeMeasurements, "Repeated measurement: [Blueing]"},
                {st, withCustard, correctTimeMeasurements, "Unexpected measurement given: [\"Custard\"]"},
                {st, correctTimeMeasurements, correctTimeMeasurements, null},
                {st, ucTimeMeasurements, correctTimeMeasurements, null},
        }).map(Arguments::of);
    }

    @Test
    public void testCreateOperation() {
        User user = EntityFactory.getUser();
        StainType st = new StainType(1, "Coffee");
        OperationType opType = new OperationType(6, "Stain");

        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 17, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(4,1);
        Labware lw = EntityFactory.makeLabware(lt, sam1, sam1, sam2, null);
        lw.getFirstSlot().setSamples(List.of(sam1, sam2));
        List<Slot> slots = lw.getSlots();
        List<Action> expectedActions = List.of(
                action(slots.get(0), sam1),
                action(slots.get(0), sam2),
                action(slots.get(1), sam1),
                action(slots.get(2), sam2)
        );
        when(mockOpService.createOperation(any(), any(), anyList(), any(), any())).then(invocation -> {
            Consumer<Operation> mut = invocation.getArgument(4);
            Operation op = new Operation(null, opType, null, expectedActions, user, null);
            mut.accept(op);
            return op;
        });
        Operation op = service.createOperation(user, lw, opType, st);
        verify(mockOpService).createOperation(eq(opType), eq(user), Matchers.sameElements(expectedActions), isNull(), isNotNull());
        assertEquals(op.getStainType(), st);
    }

    private static Action action(Slot slot, Sample sample) {
        return new Action(null, null, slot, slot, sample, sample);
    }

    @Test
    public void testCreateOperations() {
        OperationType opType = new OperationType(6, "Stain");
        User user = EntityFactory.getUser();
        StainType stainType = new StainType(1, "Coffee");
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).collect(toList());

        when(mockOpTypeRepo.getByName("Stain")).thenReturn(opType);
        List<Operation> ops = labware.stream().map(lw -> {
            Operation op = new Operation(500+lw.getId(), opType, null, List.of(), user);
            doReturn(op).when(service).createOperation(user, lw, opType, stainType);
            return op;
        }).collect(toList());

        assertEquals(ops, service.createOperations(user, labware, stainType));
        labware.forEach(lw -> verify(service).createOperation(user, lw, opType, stainType));
    }

    @Test
    public void testRecordMeasurements() {
        assertThat(service.recordMeasurements(List.of(), List.of())).isEmpty();

        List<TimeMeasurement> tms = List.of(
                new TimeMeasurement("Alpha", 20), new TimeMeasurement("Beta", 50)
        );
        int opId1 = 40;
        int opId2 = 41;
        List<Operation> ops = IntStream.of(opId1, opId2).mapToObj(
                id -> new Operation(id, null, null, null, null)
        ).collect(toList());

        List<Measurement> expectedMeasurements = List.of(
                new Measurement(null, "Alpha", "20", null, opId1, null),
                new Measurement(null, "Alpha", "20", null, opId2, null),
                new Measurement(null, "Beta", "50", null, opId1, null),
                new Measurement(null, "Beta", "50", null, opId2, null)
        );
        Iterator<Integer> intIter = IntStream.range(500, 504).iterator();
        List<Measurement> savedMeasurements = expectedMeasurements.stream()
                .map(m -> new Measurement(intIter.next(), m.getName(), m.getValue(), m.getSampleId(), m.getOperationId(), m.getSlotId()))
                .collect(toList());
        when(mockMeasurementRepo.saveAll(any())).thenReturn(savedMeasurements);

        assertSame(savedMeasurements, service.recordMeasurements(ops, tms));
        verify(mockMeasurementRepo).saveAll(Matchers.sameElements(expectedMeasurements));
    }

    @ParameterizedTest
    @MethodSource("recordStainArgs")
    public void testRecordStain(String expectedProblem, String workNumber) {
        Work work = (workNumber==null ? null : new Work(5000, workNumber, null, null, null, Work.Status.active));
        Collection<Labware> labware = List.of(EntityFactory.getTube());
        StainType stainType = new StainType(6, "Coffee");
        List<TimeMeasurement> tms = List.of(new TimeMeasurement("Blueing", 500));

        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        doReturn(labware).when(service).validateLabware(any(), any());
        doReturn(stainType).when(service).validateStainType(any(), any());
        User user = EntityFactory.getUser();
        StainRequest request = new StainRequest("Coffee", List.of("STAN-04"), List.of(new TimeMeasurement("BLUEING", 500)));
        List<Operation> ops;
        if (expectedProblem != null) {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(expectedProblem);
                return tms;
            }).when(service).validateMeasurements(any(), any(), any());

            ValidationException ex = assertThrows(ValidationException.class, () -> service.recordStain(user, request));
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).containsExactly(expectedProblem);
            ops = null;
        } else {
            doReturn(tms).when(service).validateMeasurements(any(), any(), any());

            ops = List.of(new Operation(500, null, null, null, null));
            doReturn(ops).when(service).createOperations(any(), any(), any());

            assertEquals(new OperationResult(ops, labware), service.recordStain(user, request));
        }


        verify(mockWorkService).validateUsableWork(any(), eq(request.getWorkNumber()));
        verify(service).validateLabware(any(), eq(request.getBarcodes()));
        verify(service).validateStainType(any(), eq(request.getStainType()));
        verify(service).validateMeasurements(any(), eq(stainType), eq(request.getTimeMeasurements()));

        if (expectedProblem != null) {
            verify(service, never()).createOperations(any(), any(), any());
            verify(service, never()).recordMeasurements(any(), any());
        } else {
            verify(service).createOperations(user, labware, stainType);
            verify(service).recordMeasurements(ops, tms);
        }
        if (expectedProblem==null && work!=null) {
            verify(mockWorkService).link(work, ops);
        } else {
            verify(mockWorkService, never()).link(any(Work.class), any());
            verify(mockWorkService, never()).link(anyString(), any());
        }

    }

    static Stream<Arguments> recordStainArgs() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("Stain went wrong colour.", null),
                Arguments.of(null, "SGP5000"),
                Arguments.of("Stain burned hole in table.", "SGP7000")
        );
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> argToList(Object arg) {
        if (arg==null) {
            return List.of();
        }
        if (arg instanceof List) {
            return (List<E>) arg;
        }
        return (List<E>) List.of(arg);
    }
}
