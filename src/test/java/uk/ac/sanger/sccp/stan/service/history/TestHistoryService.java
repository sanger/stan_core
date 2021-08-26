package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.History;
import uk.ac.sanger.sccp.stan.request.HistoryEntry;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link HistoryServiceImp}
 * @author dr6
 */
public class TestHistoryService {
    private OperationRepo mockOpRepo;
    private LabwareRepo mockLwRepo;
    private SampleRepo mockSampleRepo;
    private TissueRepo mockTissueRepo;
    private DonorRepo mockDonorRepo;
    private ReleaseRepo mockReleaseRepo;
    private DestructionRepo mockDestructionRepo;
    private OperationCommentRepo mockOpCommentRepo;
    private SnapshotRepo mockSnapshotRepo;
    private WorkRepo mockWorkRepo;
    private MeasurementRepo mockMeasurementRepo;

    private HistoryServiceImp service;

    private Sample[] samples;
    private Labware[] labware;
    private List<Operation> ops;

    @BeforeEach
    public void setup() {
        mockOpRepo = mock(OperationRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockDonorRepo = mock(DonorRepo.class);
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockDestructionRepo = mock(DestructionRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);
        mockSnapshotRepo = mock(SnapshotRepo.class);
        mockWorkRepo = mock(WorkRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);

        service = spy(new HistoryServiceImp(mockOpRepo, mockLwRepo, mockSampleRepo, mockTissueRepo, mockDonorRepo,
                mockReleaseRepo, mockDestructionRepo, mockOpCommentRepo, mockSnapshotRepo, mockWorkRepo, mockMeasurementRepo));
    }

    @Test
    public void testGetHistoryForSampleId() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        when(mockSampleRepo.findById(sample.getId())).thenReturn(Optional.of(sample));
        Sample sample2 = new Sample(sample.getId()+1, 10, sample.getTissue(), sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        when(mockSampleRepo.findAllByTissueIdIn(List.of(sample.getTissue().getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForSampleId(sample.getId()));

        when(mockSampleRepo.findById(-1)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getHistoryForSampleId(-1));
    }

    @Test
    public void testGetHistoryForExternalName() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        when(mockTissueRepo.getByExternalName(tissue.getExternalName())).thenReturn(tissue);
        Sample sample2 = new Sample(sample.getId()+1, 10, sample.getTissue(), sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        when(mockSampleRepo.findAllByTissueIdIn(List.of(tissue.getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForExternalName(tissue.getExternalName()));
    }

    @Test
    public void testGetHistoryForDonorName() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        final Donor donor = tissue.getDonor();
        Tissue tissue2 = EntityFactory.makeTissue(donor, EntityFactory.getSpatialLocation());
        when(mockTissueRepo.findByDonorId(donor.getId())).thenReturn(List.of(tissue, tissue2));
        when(mockDonorRepo.getByDonorName(donor.getDonorName())).thenReturn(donor);
        Sample sample2 = new Sample(sample.getId()+1, 10, tissue2, sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        when(mockSampleRepo.findAllByTissueIdIn(List.of(tissue.getId(), tissue2.getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForDonorName(donor.getDonorName()));
    }

    @Test
    public void testGetHistoryForLabwareBarcode() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue.getDonor(), EntityFactory.getSpatialLocation());
        Sample sample2 = new Sample(sample.getId()+1, 10, tissue2, sample.getBioState());
        Sample sample3 = new Sample(sample2.getId()+1, 11, tissue2, sample.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeLabware(lt, sample, sample2);
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        List<Sample> samples = List.of(sample, sample2, sample3);
        when(mockSampleRepo.findAllByTissueIdIn(Set.of(tissue.getId(), tissue2.getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForLabwareBarcode(lw.getBarcode()));
    }

    @Test
    public void testGetHistoryForSamples() {
        Sample sample = EntityFactory.getSample();
        Sample sample2 = new Sample(sample.getId() + 1, 10, sample.getTissue(), sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        Set<Integer> sampleIds = Set.of(sample.getId(), sample2.getId());
        List<Operation> ops = List.of(new Operation(100, null, null, null, null));
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeLabware(lt, samples.get(i))).collect(toList());
        Set<Integer> labwareIds = Set.of(labware.get(0).getId(), labware.get(1).getId());
        List<Destruction> destructions = List.of(new Destruction());
        List<Release> releases = List.of(new Release());
        Map<Integer, Set<String>> opWork = Map.of(1, Set.of("R&D50"), 2, Set.of());
        when(mockWorkRepo.findWorkNumbersForOpIds(Set.of(100))).thenReturn(opWork);

        List<HistoryEntry> opEntries = List.of(new HistoryEntry(1, "op", null, 1, 1, null, "user1", "R&D50"));
        List<HistoryEntry> releaseEntries = List.of(new HistoryEntry(2, "release", null, 1, 1, null, "user2", null));
        List<HistoryEntry> destructionEntries = List.of(new HistoryEntry(3, "destruction", null, 1, 1, null, "user3", null));

        List<HistoryEntry> entries = List.of(opEntries.get(0), releaseEntries.get(0), destructionEntries.get(0));

        when(mockOpRepo.findAllBySampleIdIn(sampleIds)).thenReturn(ops);
        when(mockDestructionRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(destructions);
        when(mockReleaseRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(releases);
        when(mockLwRepo.findAllByIdIn(labwareIds)).thenReturn(labware);

        doReturn(labwareIds).when(service).loadLabwareIdsForOpsAndSampleIds(ops, sampleIds);

        doReturn(opEntries).when(service).createEntriesForOps(ops, sampleIds, labware, opWork);
        doReturn(releaseEntries).when(service).createEntriesForReleases(releases, sampleIds);
        doReturn(destructionEntries).when(service).createEntriesForDestructions(destructions, sampleIds);

        doReturn(entries).when(service).assembleEntries(List.of(opEntries, releaseEntries, destructionEntries));

        History history = service.getHistoryForSamples(samples);
        assertEquals(history.getEntries(), entries);
        assertEquals(history.getSamples(), samples);
        assertEquals(history.getLabware(), labware);
    }

    private static Stream<Slot> streamSlots(Labware lw, Address... addresses) {
        return Arrays.stream(addresses).map(lw::getSlot);
    }

    private void createSamples() {
        if (samples==null) {
            Sample sample = EntityFactory.getSample();
            samples = new Sample[3];
            samples[0] = sample;
            for (int i = 1; i < samples.length; ++i) {
                samples[i] = new Sample(sample.getId()+i, 10+i, sample.getTissue(), sample.getBioState());
            }
        }
    }

    private void createLabware() {
        createSamples();
        if (labware==null) {
            LabwareType lt = EntityFactory.makeLabwareType(3,1);
            int[][] samplesInLabware = {{0,1,2},{0,1},{1},{2}};
            labware = Arrays.stream(samplesInLabware)
                    .map(arr -> Arrays.stream(arr).mapToObj(i -> samples[i]).toArray(Sample[]::new))
                    .map(contents -> EntityFactory.makeLabware(lt, contents))
                    .toArray(Labware[]::new);
        }
    }

    private User getUser() {
        return EntityFactory.getUser();
    }

    // Op 1: samples 0,1 from labware 0 to labware 1
    //       sample 1 from labware 0 to labware 2
    // Op 2: sample 2 from labware 0 to labware 3
    // We are not interested in sample 1, so we should get labware 0, 1 and 3 returned.
    private void createOps() {
        createLabware();
        if (ops==null) {
            final Address A1 = new Address(1,1);
            final Address B1 = new Address(2,1);
            final Address C1 = new Address(3,1);

            OperationType opType = EntityFactory.makeOperationType("Catapult", null);
            ops = List.of(
                    EntityFactory.makeOpForSlots(opType, streamSlots(labware[0], A1, B1, B1).collect(toList()),
                            Stream.concat(streamSlots(labware[1], A1, B1), streamSlots(labware[2], A1)).collect(toList()), getUser()),
                    EntityFactory.makeOpForSlots(opType, List.of(labware[0].getSlot(C1)), List.of(labware[3].getFirstSlot()), getUser())
            );
        }
    }

    @Test
    public void testLoadLabwareIdsForOpsAndSampleIds() {
        createOps();
        // Op 1: samples 0,1 from labware 0 to labware 1
        //       sample 1 from labware 0 to labware 2
        // Op 2: sample 2 from labware 0 to labware 3

        Set<Integer> sampleIds = Set.of(samples[0].getId(), samples[2].getId());
        // We are not interested in sample 1, so we should get labware 0, 1 and 3 returned.

        assertEquals(Set.of(labware[0].getId(), labware[1].getId(), labware[3].getId()),
                service.loadLabwareIdsForOpsAndSampleIds(ops, sampleIds));
    }

    @Test
    public void testLoadOpComments() {
        Comment com = new Comment(1, "Purple", "Observation");

        List<OperationComment> opComs = List.of(
                new OperationComment(1, com, 1, 1, 1, 1),
                new OperationComment(2, com, 1, 2, 2, 2),
                new OperationComment(3, com, 2, 3, 3, 3)
        );
        when(mockOpCommentRepo.findAllByOperationIdIn(any())).then(invocation -> {
            Collection<Integer> opIds = invocation.getArgument(0);
            return opComs.stream().filter(oc -> opIds.contains(oc.getOperationId())).collect(toList());
        });

        assertEquals(Map.of(1, opComs.subList(0,2), 2, opComs.subList(2,3)), service.loadOpComments(Set.of(1,2,3)));
        assertEquals(Map.of(), service.loadOpComments(Set.of(3,4)));
    }

    @ParameterizedTest
    @MethodSource("doesCommentApplyData")
    public void testDoesCommentApply(OperationComment opCom, int sampleId, int labwareId, Slot slot, boolean expected) {
        Map<Integer, Slot> slotIdMap = (slot==null ? Map.of() : Map.of(slot.getId(), slot));
        assertEquals(expected, service.doesCommentApply(opCom, sampleId, labwareId, slotIdMap));
    }

    static Stream<Arguments> doesCommentApplyData() {
        Slot slot = new Slot(100, 10, new Address(1,1), null, null, null);
        Comment com = new Comment(1, "Alabama", "Bananas");
        return Stream.of(
                Arguments.of(new OperationComment(1, com, 1, null, null, null), 1, 10, null, true),
                Arguments.of(new OperationComment(1, com, 1, 1, 100, 10), 1, 10, slot, true),
                Arguments.of(new OperationComment(1, com, 1, 1, 100, null), 1, 10, slot, true),
                Arguments.of(new OperationComment(1, com, 1, null, 100, null), 1, 10, slot, true),
                Arguments.of(new OperationComment(1, com, 1, 1, null, null), 1, 10, null, true),

                Arguments.of(new OperationComment(1, com, 1, 2, null, null), 1, 10, null, false), // wrong sample id
                Arguments.of(new OperationComment(1, com, 1, null, 200, null), 1, 10, slot, false), // wrong slot id
                Arguments.of(new OperationComment(1, com, 1, null, 100, null), 1, 20, slot, false), // slot belongs to wrong labware
                Arguments.of(new OperationComment(1, com, 1, null, null, 20), 1, 10, null, false) // wrong labware id
        );
    }

    @Test
    public void testCreateEntriesForOps() {
        Comment[] coms = {
                new Comment(1, "Alabama", "Bananas"),
                new Comment(2, "Alaska", "Bananas"),
                new Comment(3, "Arizona", "Bananas"),
                new Comment(3, "Arkansas", "Bananas"),
        };
        createOps();
        int[] opIds = ops.stream().mapToInt(Operation::getId).toArray();
        Map<Integer, Set<String>> opWork = Map.of(
                opIds[0], Set.of("SGP5000"),
                opIds[1], Set.of()
        );

        OperationComment[] opComs = {
                new OperationComment(1, coms[0], opIds[0], null, null, null),
                new OperationComment(2, coms[1], opIds[0], null, labware[1].getFirstSlot().getId(), null),
                new OperationComment(3, coms[2], opIds[1], null, null, null),
                new OperationComment(4, coms[3], opIds[1], 400, null, null),
        };

        Map<Integer, List<OperationComment>> opComMap = Map.of(
                opIds[0], List.of(opComs[0], opComs[1]),
                opIds[1], List.of(opComs[2], opComs[3])
        );

        doReturn(opComMap).when(service).loadOpComments(Set.of(opIds[0], opIds[1]));

        // Letting doesCommentApply actually run is easier than mocking it to return what it would return anyway

        List<Labware> labwareList = Arrays.asList(this.labware);

        Set<Integer> sampleIds = Set.of(samples[0].getId(), samples[2].getId());

        String opTypeName = ops.get(0).getOperationType().getName();
        String username = getUser().getUsername();
        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(opIds[0], opTypeName, ops.get(0).getPerformed(), labware[0].getId(),
                        labware[1].getId(), samples[0].getId(), username, "SGP5000", List.of("Alabama", "A1: Alaska")),
                new HistoryEntry(opIds[1], opTypeName, ops.get(1).getPerformed(), labware[0].getId(),
                        labware[3].getId(), samples[2].getId(), username, null, List.of("Arizona"))
        );
        assertThat(service.createEntriesForOps(ops, sampleIds, labwareList, opWork)).containsExactlyElementsOf(expectedEntries);
    }

    private static LocalDateTime makeTime(int n) {
        return LocalDateTime.of(2021,7,1+n, 2+n, 0);
    }

    @Test
    public void testCreateEntriesForReleases() {
        createSamples();
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples[0], samples[0], samples[1], samples[2]);
        Labware lw2 = EntityFactory.makeLabware(lt, samples[0], samples[1]);
        Snapshot snap = new Snapshot(1, lw1.getId(),
                lw1.getSlots().stream().flatMap(slot -> slot.getSamples().stream()
                            .map(sample -> new SnapshotElement(null, 1, slot.getId(), sample.getId()))
                ).collect(toList()));
        User user = getUser();
        Release rel1 = new Release(1, lw1, user, new ReleaseDestination(1, "Mercury"),
                new ReleaseRecipient(2, "jeff"), snap.getId(), makeTime(1));
        Release rel2 = new Release(2, lw2, user, new ReleaseDestination(3, "Venus"),
                new ReleaseRecipient(4, "dirk"), null, makeTime(2));
        String username = user.getUsername();

        when(mockSnapshotRepo.findAllByIdIn(Set.of(1))).thenReturn(List.of(snap));
        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(1, "Release", rel1.getReleased(), lw1.getId(), lw1.getId(), samples[0].getId(),
                        username, null, List.of("Destination: Mercury", "Recipient: jeff")),
                new HistoryEntry(1, "Release", rel1.getReleased(), lw1.getId(), lw1.getId(), samples[2].getId(),
                        username, null, List.of("Destination: Mercury", "Recipient: jeff")),
                new HistoryEntry(2, "Release", rel2.getReleased(), lw2.getId(), lw2.getId(), null,
                        username, null, List.of("Destination: Venus", "Recipient: dirk"))
        );
        assertThat(service.createEntriesForReleases(List.of(rel1, rel2), Set.of(samples[0].getId(), samples[2].getId())))
                .containsExactlyElementsOf(expectedEntries);
    }

    @Test
    public void testCreateEntriesForDestructions() {
        createSamples();
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples[0], samples[0], samples[1], samples[2]);
        Labware lw2 = EntityFactory.makeLabware(lt, samples[0], samples[1]);
        User user = getUser();
        Destruction d1 = new Destruction(1, lw1, user, makeTime(1),
                new DestructionReason(1, "Dropped."));
        Destruction d2 = new Destruction(2, lw2, user, makeTime(2),
                new DestructionReason(2, "Sat on."));
        String username = user.getUsername();

        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(1, "Destruction", d1.getDestroyed(), lw1.getId(), lw1.getId(), samples[0].getId(),
                        username, null, List.of("Reason: Dropped.")),
                new HistoryEntry(1, "Destruction", d1.getDestroyed(), lw1.getId(), lw1.getId(), samples[2].getId(),
                        username, null, List.of("Reason: Dropped.")),
                new HistoryEntry(2, "Destruction", d2.getDestroyed(), lw2.getId(), lw2.getId(), samples[0].getId(),
                        username, null, List.of("Reason: Sat on."))
        );

        assertThat(service.createEntriesForDestructions(List.of(d1, d2), Set.of(samples[0].getId(), samples[2].getId())))
                .containsExactlyElementsOf(expectedEntries);
    }


    @Test
    public void testAssembleEntries() {
        List<HistoryEntry> e1 = List.of(
                new HistoryEntry(1, "Register", makeTime(0), 1, 1, 1, "user1", null),
                new HistoryEntry(2, "Section", makeTime(2), 1, 2, 2, "user2", null),
                new HistoryEntry(3, "Section", makeTime(4), 1, 3, 3, "user3", null),
                new HistoryEntry(4, "Scoop", makeTime(6), 3, 4, 3, "user4", null)
        );
        List<HistoryEntry> e2 = List.of(
                new HistoryEntry(1, "Release", makeTime(3), 2, 2, 2, "user11", null),
                new HistoryEntry(2, "Release", makeTime(8), 3, 3, 3, "user12", null)
        );
        List<HistoryEntry> e3 = List.of(
                new HistoryEntry(1, "Destruction", makeTime(5), 1, 1, 1, "user21", null),
                new HistoryEntry(2, "Destruction", makeTime(9), 4, 4, 3, "user22", null)
        );

        List<HistoryEntry> expectedEntries = List.of(
                e1.get(0), e1.get(1), e2.get(0), e1.get(2), e3.get(0), e1.get(3), e2.get(1), e3.get(1)
        );

        assertEquals(expectedEntries, service.assembleEntries(List.of(e1, e2, e3)));
    }
}
