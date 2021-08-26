package uk.ac.sanger.sccp.stan.service.history;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.History;
import uk.ac.sanger.sccp.stan.request.HistoryEntry;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class HistoryServiceImp implements HistoryService {
    private final OperationRepo opRepo;
    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final TissueRepo tissueRepo;
    private final DonorRepo donorRepo;
    private final ReleaseRepo releaseRepo;
    private final DestructionRepo destructionRepo;
    private final OperationCommentRepo opCommentRepo;
    private final SnapshotRepo snapshotRepo;
    private final WorkRepo workRepo;
    private final MeasurementRepo measurementRepo;

    @Autowired
    public HistoryServiceImp(OperationRepo opRepo, LabwareRepo lwRepo, SampleRepo sampleRepo, TissueRepo tissueRepo,
                             DonorRepo donorRepo, ReleaseRepo releaseRepo,
                             DestructionRepo destructionRepo, OperationCommentRepo opCommentRepo,
                             SnapshotRepo snapshotRepo, WorkRepo workRepo, MeasurementRepo measurementRepo) {
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.tissueRepo = tissueRepo;
        this.donorRepo = donorRepo;
        this.releaseRepo = releaseRepo;
        this.destructionRepo = destructionRepo;
        this.opCommentRepo = opCommentRepo;
        this.snapshotRepo = snapshotRepo;
        this.workRepo = workRepo;
        this.measurementRepo = measurementRepo;
    }

    @Override
    public History getHistoryForSampleId(int sampleId) {
        Sample sample = sampleRepo.findById(sampleId).orElseThrow(() -> new EntityNotFoundException("Sample id "+ sampleId +" not found."));
        Tissue tissue = sample.getTissue();
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(List.of(tissue.getId()));
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForExternalName(String externalName) {
        Tissue tissue = tissueRepo.getByExternalName(externalName);
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(List.of(tissue.getId()));
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForDonorName(String donorName) {
        Donor donor = donorRepo.getByDonorName(donorName);
        List<Tissue> tissues = tissueRepo.findByDonorId(donor.getId());
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(tissues.stream().map(Tissue::getId).collect(Collectors.toList()));
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForLabwareBarcode(String barcode) {
        Labware lw = lwRepo.getByBarcode(barcode);
        Set<Integer> tissueIds = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .map(sam -> sam.getTissue().getId())
                .collect(toSet());
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(tissueIds);
        return getHistoryForSamples(samples);
    }

    /**
     * Gets the history for the specifically supplied samples (which are commonly all related).
     * @param samples the samples to get the history for
     * @return the history involving those samples
     */
    public History getHistoryForSamples(List<Sample> samples) {
        Set<Integer> sampleIds = samples.stream().map(Sample::getId).collect(toSet());
        List<Operation> ops = opRepo.findAllBySampleIdIn(sampleIds);
        Set<Integer> labwareIds = loadLabwareIdsForOpsAndSampleIds(ops, sampleIds);
        List<Destruction> destructions = destructionRepo.findAllByLabwareIdIn(labwareIds);
        List<Release> releases = releaseRepo.findAllByLabwareIdIn(labwareIds);
        List<Labware> labware = lwRepo.findAllByIdIn(labwareIds);

        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        Map<Integer, Set<String>> opWork = workRepo.findWorkNumbersForOpIds(opIds);

        List<HistoryEntry> opEntries = createEntriesForOps(ops, sampleIds, labware, opWork);
        List<HistoryEntry> releaseEntries = createEntriesForReleases(releases, sampleIds);
        List<HistoryEntry> destructionEntries = createEntriesForDestructions(destructions, sampleIds);

        List<HistoryEntry> entries = assembleEntries(List.of(opEntries, releaseEntries, destructionEntries));
        return new History(entries, samples, labware);
    }


    /**
     * Gets labware ids from the given operations that are linked to the given sample ids.
     * @param ops the operations
     * @param sampleIds the ids of relevant samples
     * @return the labware ids referenced in the operations related to the sample ids
     */
    public Set<Integer> loadLabwareIdsForOpsAndSampleIds(Collection<Operation> ops, Set<Integer> sampleIds) {
        Set<Integer> labwareIds = new HashSet<>();
        for (Operation op : ops) {
            for (Action action : op.getActions()) {
                if (action.getSample()!=null && sampleIds.contains(action.getSample().getId())
                        || action.getSourceSample()!=null && sampleIds.contains(action.getSourceSample().getId())) {
                    labwareIds.add(action.getDestination().getLabwareId());
                    labwareIds.add(action.getSource().getLabwareId());
                }
            }
        }
        return labwareIds;
    }

    /**
     * Gets all comments on specified operations
     * @param opIds ids of operations
     * @return a map of operation id to a list of operation comments
     */
    public Map<Integer, List<OperationComment>> loadOpComments(Collection<Integer> opIds) {
        List<OperationComment> opComments = opCommentRepo.findAllByOperationIdIn(opIds);
        if (opComments.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<OperationComment>> map = new HashMap<>();
        for (OperationComment oc : opComments) {
            map.computeIfAbsent(oc.getOperationId(), k -> new ArrayList<>()).add(oc);
        }
        return map;
    }

    /**
     * Should the given operation-comment be added as a detail to the history entry under construction?
     * It should, unless it has a field that indicates it is not relevant.
     * @param com the operation comment to be checked
     * @param sampleId the id of the sample for the history entry
     * @param labwareId the id of the destination labware for the history entry
     * @param slotIdMap a map to look up slots from their id
     * @return true if the comment is applicable; false if it is not
     */
    public boolean doesCommentApply(OperationComment com, int sampleId, int labwareId, Map<Integer, Slot> slotIdMap) {
        if (com.getSampleId()!=null && com.getSampleId()!=sampleId) {
            return false;
        }
        if (com.getLabwareId()!=null) {
            return (com.getLabwareId()==labwareId);
        }
        if (com.getSlotId()!=null) {
            Slot slot = slotIdMap.get(com.getSlotId());
            return (slot!=null && slot.getLabwareId() == labwareId);
        }
        return true;
    }

    /**
     * Should the given measurement be added as a detail to the history entry under construction?
     * It should, unless it has a field that indicates it is not relevant.
     * @param measurement the measurement to be checked
     * @param sampleId the id of the sample for the history entry
     * @param labwareId the id of the destination labware for the history entry
     * @param slotIdMap a map to look up slots from their id
     * @return true if the measurement is applicable; false if it is not
     */
    public boolean doesMeasurementApply(Measurement measurement, int sampleId, int labwareId, Map<Integer, Slot> slotIdMap) {
        if (measurement.getSampleId()!=null && measurement.getSampleId()!=sampleId) {
            return false;
        }
        if (measurement.getSlotId()!=null) {
            Slot slot = slotIdMap.get(measurement.getSlotId());
            return (slot!=null && slot.getLabwareId()==labwareId);
        }
        return true;
    }

    /**
     * Loads measurements for the given operations
     * @param opIds the id of operations
     * @return a map of operation id to list of measurements
     */
    public Map<Integer, List<Measurement>> loadOpMeasurements(Collection<Integer> opIds) {
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(opIds);
        if (measurements.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Measurement>> map = new HashMap<>(opIds.size());
        for (Measurement measurement : measurements) {
            map.computeIfAbsent(measurement.getOperationId(), k -> new ArrayList<>()).add(measurement);
        }
        return map;
    }

    public String describeSeconds(String value) {
        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            return value;
        }
        int minutes = seconds/60;
        if (minutes==0) {
            return seconds+" sec";
        }
        seconds %= 60;
        int hours = minutes/60;
        if (hours==0) {
            return minutes+" min "+seconds+" sec";
        }
        minutes %= 60;
        return String.format("%d hour %d min %d sec", hours, minutes, seconds);
    }

    /**
     * Converts a measurement into a string to go into the details of a history entry
     * @param measurement a measurement
     * @param slotIdMap a map to look up slot ids
     * @return a string describing the measurement
     */
    public String measurementDetail(Measurement measurement, Map<Integer, Slot> slotIdMap) {
        MeasurementType mt = MeasurementType.forName(measurement.getName());
        MeasurementValueType vt = (mt==null ? null : mt.getValueType());
        String detail = measurement.getName() + ": ";
        if (vt==MeasurementValueType.TIME) {
            detail += describeSeconds(measurement.getValue());
        } else {
            detail += measurement.getValue();
        }
        if (measurement.getSlotId()!=null) {
            assert slotIdMap != null;
            detail = slotIdMap.get(measurement.getSlotId()).getAddress()+": "+detail;
        }
        return detail;
    }

    /**
     * Creates history entries for the given operations, where relevant to the specified samples
     * @param operations the operations to represent in the history
     * @param sampleIds the ids of relevant samples
     * @param labware the relevant labware
     * @param opWork a map of op id to work numbers
     * @return a list history entries for the given operations
     */
    public List<HistoryEntry> createEntriesForOps(Collection<Operation> operations, Set<Integer> sampleIds,
                                                  Collection<Labware> labware, Map<Integer, Set<String>> opWork) {
        Set<Integer> opIds = operations.stream().map(Operation::getId).collect(toSet());
        var opComments = loadOpComments(opIds);
        var opMeasurements = loadOpMeasurements(opIds);
        final Map<Integer, Slot> slotIdMap;
        if (!opComments.isEmpty() || !opMeasurements.isEmpty()) {
            slotIdMap = labware.stream()
                    .flatMap(lw -> lw.getSlots().stream())
                    .collect(toMap(Slot::getId, Function.identity()));
        } else {
            slotIdMap = null; // not needed
        }
        List<HistoryEntry> entries = new ArrayList<>();
        for (Operation op : operations) {
            String stainDetail;
            if (op.getOperationType().has(OperationTypeFlag.STAIN)) {
                StainType st = op.getStainType();
                stainDetail = (st != null ? "Stain type: "+st.getName() : null);
            } else {
                stainDetail = null;
            }
            Set<SampleAndLabwareIds> items = new LinkedHashSet<>();
            List<OperationComment> comments = opComments.getOrDefault(op.getId(), List.of());
            List<Measurement> measurements = opMeasurements.getOrDefault(op.getId(), List.of());
            String workNumber;
            Set<String> workNumbers = opWork.get(op.getId());
            if (workNumbers!=null && !workNumbers.isEmpty()) {
                workNumber = String.join(", ", workNumbers);
            } else {
                workNumber = null;
            }

            for (Action action : op.getActions()) {
                final Integer sampleId = action.getSample().getId();
                if (sampleIds.contains(sampleId)) {
                    final Integer sourceId = action.getSource().getLabwareId();
                    final Integer destId = action.getDestination().getLabwareId();
                    items.add(new SampleAndLabwareIds(sampleId, sourceId, destId));
                }
            }
            String username = op.getUser().getUsername();
            for (var item : items) {
                HistoryEntry entry = new HistoryEntry(op.getId(), op.getOperationType().getName(),
                        op.getPerformed(), item.sourceId, item.destId, item.sampleId, username, workNumber);
                if (stainDetail!=null) {
                    entry.addDetail(stainDetail);
                }
                comments.forEach(com -> {
                    if (doesCommentApply(com, item.sampleId, item.destId, slotIdMap)) {
                        String detail = com.getComment().getText();
                        if (com.getSlotId()!=null) {
                            assert slotIdMap != null;
                            detail = slotIdMap.get(com.getSlotId()).getAddress()+": "+detail;
                        }
                        entry.addDetail(detail);
                    }
                });
                measurements.forEach(measurement -> {
                    if (doesMeasurementApply(measurement, item.sampleId, item.destId, slotIdMap)) {
                        String detail = measurementDetail(measurement, slotIdMap);
                        entry.addDetail(detail);
                    }
                });

                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Creates history entries representing releases, where they are applicable to the specified sample
     * @param releases the releases to represent
     * @param sampleIds the ids of relevant samples
     * @return a list of history entries for the given releases
     */
    public List<HistoryEntry> createEntriesForReleases(List<Release> releases, Set<Integer> sampleIds) {
        Set<Integer> snapshotIds = releases.stream().map(Release::getSnapshotId).filter(Objects::nonNull).collect(toSet());
        Map<Integer, Snapshot> snapshotMap = snapshotRepo.findAllByIdIn(snapshotIds).stream()
                .collect(BasicUtils.toMap(Snapshot::getId, HashMap::new));
        List<HistoryEntry> entries = new ArrayList<>();
        for (Release release : releases) {
            int labwareId = release.getLabware().getId();
            List<String> details = List.of("Destination: "+release.getDestination().getName(),
                    "Recipient: "+release.getRecipient().getUsername());
            String username = release.getUser().getUsername();
            if (release.getSnapshotId()==null) {
                entries.add(new HistoryEntry(release.getId(), "Release", release.getReleased(),
                        labwareId, labwareId,null, username, null, details));
            } else {
                Snapshot snap = snapshotMap.get(release.getSnapshotId());
                Set<Integer> releaseSampleIds = snap.getElements().stream()
                        .map(SnapshotElement::getSampleId)
                        .filter(sampleIds::contains)
                        .collect(BasicUtils.toLinkedHashSet());
                for (Integer sampleId : releaseSampleIds) {
                    entries.add(new HistoryEntry(release.getId(), "Release", release.getReleased(),
                            labwareId, labwareId, sampleId, username, null, details));
                }
            }
        }
        return entries;
    }

    /**
     * Creates history entries representing destructions, where they are applicable to the specified sample
     * @param destructions the destructions to represent
     * @param sampleIds the ids of relevant samples
     * @return a list of history entries for the given destructions
     */
    public List<HistoryEntry> createEntriesForDestructions(Collection<Destruction> destructions, Set<Integer> sampleIds) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (Destruction destruction : destructions) {
            final Labware labware = destruction.getLabware();
            Set<Integer> destructionSampleIds = labware.getSlots().stream()
                    .flatMap(slot -> slot.getSamples().stream().map(Sample::getId))
                    .filter(sampleIds::contains)
                    .collect(BasicUtils.toLinkedHashSet());
            if (!destructionSampleIds.isEmpty()) {
                String username = destruction.getUser().getUsername();
                int labwareId = labware.getId();
                List<String> details = List.of("Reason: "+destruction.getReason().getText());
                for (Integer sampleId : destructionSampleIds) {
                    entries.add(new HistoryEntry(destruction.getId(), "Destruction", destruction.getDestroyed(),
                            labwareId, labwareId, sampleId, username, null, details));
                }
            }
        }
        return entries;
    }

    /**
     * Assembles various collections of history enties into a single list, sorted by time
     * @param entryCollections the collections of entries to include in the combined list
     * @return a list containing the entries from all the given collections, sorted
     */
    public List<HistoryEntry> assembleEntries(List<Collection<HistoryEntry>> entryCollections) {
        int num = entryCollections.stream().mapToInt(Collection::size).sum();
        List<HistoryEntry> entries = new ArrayList<>(num);
        for (var entryList : entryCollections) {
            entries.addAll(entryList);
        }
        entries.sort(Comparator.comparing(HistoryEntry::getTime));
        return entries;
    }

    // region support class
    private static class SampleAndLabwareIds {
        final int sampleId, sourceId, destId;

        public SampleAndLabwareIds(int sampleId, int sourceId, int destId) {
            this.sampleId = sampleId;
            this.sourceId = sourceId;
            this.destId = destId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SampleAndLabwareIds that = (SampleAndLabwareIds) o;
            return (this.sampleId == that.sampleId
                    && this.sourceId == that.sourceId
                    && this.destId == that.destId);
        }

        @Override
        public int hashCode() {
            return sampleId + 31 * (sourceId + 31 * destId);
        }
    }
    // endregion
}
