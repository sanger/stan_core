package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.StainRequest;
import uk.ac.sanger.sccp.stan.request.stain.TimeMeasurement;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class StainServiceImp implements StainService {
    private final StainTypeRepo stainTypeRepo;
    private final LabwareRepo labwareRepo;
    private final OperationTypeRepo opTypeRepo;
    private final MeasurementRepo measurementRepo;

    private final LabwareValidatorFactory labwareValidatorFactory;
    private final WorkService workService;
    private final OperationService opService;

    public StainServiceImp(StainTypeRepo stainTypeRepo, LabwareRepo labwareRepo, OperationTypeRepo opTypeRepo,
                           MeasurementRepo measurementRepo,
                           LabwareValidatorFactory labwareValidatorFactory, WorkService workService,
                           OperationService opService) {
        this.stainTypeRepo = stainTypeRepo;
        this.labwareRepo = labwareRepo;
        this.opTypeRepo = opTypeRepo;
        this.measurementRepo = measurementRepo;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.workService = workService;
        this.opService = opService;
    }

    @Override
    public List<StainType> getEnabledStainTypes() {
        return stainTypeRepo.findAllByEnabled(true);
    }

    /**
     * Looks up the labware and checks for any problems
     * @param problems the receptacle for problems
     * @param barcodes the labware barcodes to look up
     * @return the loaded labware
     */
    public Collection<Labware> validateLabware(Collection<String> problems, Collection<String> barcodes) {
        if (barcodes==null || barcodes.isEmpty()) {
            problems.add("No barcodes supplied.");
            return List.of();
        }
        LabwareValidator validator = labwareValidatorFactory.getValidator();
        validator.setUniqueRequired(true);
        validator.loadLabware(labwareRepo, barcodes);
        validator.validateSources();
        problems.addAll(validator.getErrors());
        return validator.getLabware();
    }

    /**
     * Loads the indicated stain type
     * @param problems the receptacle for problems
     * @param name the name of the stain type
     * @return the stain type, if found; null if it is not found
     */
    public StainType validateStainType(Collection<String> problems, String name) {
        if (name==null || name.isEmpty()) {
            problems.add("No stain type specified.");
            return null;
        }
        Optional<StainType> optSt = stainTypeRepo.findByName(name);
        if (optSt.isEmpty()) {
            problems.add("Stain type not found: "+repr(name));
            return null;
        }
        return optSt.get();
    }

    /**
     * Validates the measurements
     * @param problems the receptacle for problems
     * @param stainType the stain type requested
     * @param timeMeasurements the requested time measurements
     * @return the time measurements, with the name sanitised to the expected form
     */
    public List<TimeMeasurement> validateMeasurements(Collection<String> problems, StainType stainType, Collection<TimeMeasurement> timeMeasurements) {
        if (stainType==null) {
            return List.of(); // cannot validate measurements without a stain type
        }
        if (timeMeasurements==null || timeMeasurements.isEmpty()) {
            if (!stainType.getMeasurementTypes().isEmpty()) {
                problems.add("No measurements supplied for stain.");
            }
            return List.of();
        }
        List<String> expectedMeasurementTypes = stainType.getMeasurementTypes();
        if (expectedMeasurementTypes.isEmpty()) {
            problems.add("Measurements are not expected for stain type "+stainType.getName()+".");
            return List.of();
        }
        UCMap<String> caseMap = UCMap.from(expectedMeasurementTypes, Function.identity());
        Set<String> repeated = new HashSet<>();
        Map<String, Integer> measurementMap = new HashMap<>();
        Set<String> unknown = new HashSet<>();
        boolean anyBadValues = false;
        for (TimeMeasurement tm : timeMeasurements) {
            String mt = caseMap.get(tm.getName());
            if (mt==null) {
                unknown.add(tm.getName());
                continue;
            }
            if (tm.getSeconds() <= 0) {
                anyBadValues = true;
            }
            if (measurementMap.containsKey(mt)) {
                repeated.add(mt);
            }
            measurementMap.put(mt, tm.getSeconds());
        }
        Set<String> missing = caseMap.values().stream()
                .filter(mt -> !measurementMap.containsKey(mt))
                .collect(toSet());
        if (!repeated.isEmpty()) {
            problems.add("Repeated measurement: "+repeated);
        }
        if (!unknown.isEmpty()) {
            problems.add("Unexpected measurement given: "+BasicUtils.reprCollection(unknown));
        }
        if (!missing.isEmpty()) {
            problems.add("Missing measurements: "+missing);
        }
        if (anyBadValues) {
            problems.add("Time measurements must be greater than zero.");
        }
        return measurementMap.entrySet().stream()
                .map(e -> new TimeMeasurement(e.getKey(), e.getValue()))
                .collect(toList());
    }

    /**
     * Creates the specified stain operations
     * @param user the user recording the operations
     * @param labware the labware
     * @param stainType the stain type
     * @return the newly created operations
     */
    public List<Operation> createOperations(User user, Collection<Labware> labware, StainType stainType) {
        OperationType opType = opTypeRepo.getByName("Stain");
        return labware.stream()
                .map(lw -> createOperation(user, lw, opType, stainType))
                .collect(toList());
    }

    /**
     * Creates an operation with a stain type
     * @param user the user recording the operation
     * @param labware the item of labware
     * @param opType the operation type
     * @param stainType the stain type for the operation
     * @return the newly created operation
     */
    public Operation createOperation(User user, Labware labware, OperationType opType, StainType stainType) {
        List<Action> actions = labware.getSlots().stream()
                        .flatMap(slot -> slot.getSamples().stream()
                                .map(sample -> new Action(null, null, slot, slot, sample, sample)))
                                .collect(toList());
        return opService.createOperation(opType, user, actions, null, op -> op.setStainType(stainType));
    }

    /**
     * Records measurements against the given operations
     * @param ops the operations
     * @param tms the specification of the time measurements
     * @return the newly created measurements
     */
    public Iterable<Measurement> recordMeasurements(Collection<Operation> ops, Collection<TimeMeasurement> tms) {
        if (tms.isEmpty()) {
            return List.of();
        }
        List<Measurement> measurements = ops.stream().map(Operation::getId)
                .flatMap(opId -> tms.stream().map(tm ->
                        new Measurement(null, tm.getName(), String.valueOf(tm.getSeconds()), null, opId, null)
                ))
                .collect(toList());
        return measurementRepo.saveAll(measurements);
    }

    @Override
    public OperationResult recordStain(User user, StainRequest request) {
        Collection<String> problems = new LinkedHashSet<>();
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Collection<Labware> labware = validateLabware(problems, request.getBarcodes());
        StainType stainType = validateStainType(problems, request.getStainType());
        List<TimeMeasurement> measurements = validateMeasurements(problems, stainType, request.getTimeMeasurements());

        if (!problems.isEmpty()) {
            throw new ValidationException("The stain request could not be validated.", problems);
        }

        List<Operation> ops = createOperations(user, labware, stainType);
        recordMeasurements(ops, measurements);
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labware);
    }
}
