package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyDestination;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Service to perform an operation copying the contents of slots to new labware
 * @author dr6
 */
@Service
public class SlotCopyServiceImp implements SlotCopyService {
    static final String CYTASSIST_OP = "CytAssist";
    static final String CYTASSIST_SLIDE = "Visium LP CytAssist", CYTASSIST_SLIDE_XL = "Visium LP CytAssist XL";

    static final String BS_PROBES = "Probes", BS_CDNA = "cDNA", BS_LIBRARY = "Library",
            BS_LIB_PRE_CLEAN = "Library pre-clean", BS_LIB_POST_CLEAN = "Library post-clean",
            BS_PROBES_PRE_CLEAN = "Probes pre-clean", BS_PROBES_POST_CLEAN = "Probes post-clean";

    static final Set<String> VALID_BS_UPPER = Stream.of(
                    BS_PROBES, BS_CDNA, BS_LIBRARY, BS_LIB_PRE_CLEAN, BS_LIB_POST_CLEAN,
                    BS_PROBES_PRE_CLEAN, BS_PROBES_POST_CLEAN
            ).map(String::toUpperCase)
            .collect(toSet());

    private final OperationTypeRepo opTypeRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final BioStateRepo bsRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final LabwareService lwService;
    private final OperationService opService;
    private final StoreService storeService;
    private final WorkService workService;
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final EntityManager entityManager;
    private final Transactor transactor;
    private final Validator<String> preBarcodeValidator;
    private final Validator<String> lotNumberValidator;

    @Autowired
    public SlotCopyServiceImp(OperationTypeRepo opTypeRepo, LabwareTypeRepo lwTypeRepo, LabwareRepo lwRepo,
                              SampleRepo sampleRepo, SlotRepo slotRepo, BioStateRepo bsRepo, LabwareNoteRepo lwNoteRepo,
                              LabwareService lwService, OperationService opService, StoreService storeService,
                              WorkService workService,
                              LabwareValidatorFactory labwareValidatorFactory, EntityManager entityManager,
                              Transactor transactor,
                              @Qualifier("cytAssistBarcodeValidator") Validator<String> preBarcodeValidator,
                              @Qualifier("lotNumberValidator") Validator<String> lotNumberValidator) {
        this.opTypeRepo = opTypeRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.bsRepo = bsRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.lwService = lwService;
        this.opService = opService;
        this.storeService = storeService;
        this.workService = workService;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.entityManager = entityManager;
        this.transactor = transactor;
        this.preBarcodeValidator = preBarcodeValidator;
        this.lotNumberValidator = lotNumberValidator;
    }

    @Override
    public OperationResult perform(User user, SlotCopyRequest request) throws ValidationException {
        Set<String> barcodesToUnstore = new HashSet<>();
        OperationResult result = transactor.transact("SlotCopy",
                () -> performInsideTransaction(user, request, barcodesToUnstore));
        if (!result.getOperations().isEmpty() && !barcodesToUnstore.isEmpty()) {
            storeService.discardStorage(user, barcodesToUnstore);
        }
        return result;
    }

    /**
     * This method is called inside a transaction to validate and execute the given request.
     * @param user the user responsible for the request
     * @param request the request to perform
     * @param barcodesToUnstore receptacle for labware barcodes that need to be removed from storage
     * @return the labware and operations created
     * @exception ValidationException validation fails
     */
    public OperationResult performInsideTransaction(User user, SlotCopyRequest request, final Set<String> barcodesToUnstore)
            throws ValidationException {
        Collection<String> problems = new LinkedHashSet<>();
        OperationType opType = loadEntity(problems, request.getOperationType(), "operation type", opTypeRepo::findByName);
        UCMap<Labware> existingDestinations = loadExistingDestinations(problems, opType, request.getDestinations());
        UCMap<LabwareType> lwTypes = loadLabwareTypes(problems, request.getDestinations());
        checkPreBarcodes(problems, request.getDestinations(), lwTypes);
        checkPreBarcodesInUse(problems, request.getDestinations(), existingDestinations);
        UCMap<Labware> sourceMap = loadSources(problems, request);
        validateSources(problems, sourceMap.values());
        UCMap<Labware.State> sourceStateMap = checkListedSources(problems, request);
        validateLotNumbers(problems, request.getDestinations());
        validateContents(problems, lwTypes, sourceMap, existingDestinations, request);
        validateOps(problems, request.getDestinations(), opType, lwTypes);
        UCMap<BioState> bs = validateBioStates(problems, request.getDestinations());
        checkExistingDestinations(problems, request.getDestinations(), existingDestinations, lwTypes, bs);
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        if (!problems.isEmpty()) {
            throw new ValidationException("The operation could not be validated.", problems);
        }
        OperationResult opres = executeOps(user, request.getDestinations(), opType, lwTypes, bs, sourceMap, work,
                existingDestinations);
        final Labware.State newSourceState = (opType.discardSource() ? Labware.State.discarded
                : opType.markSourceUsed() ? Labware.State.used : null);
        updateSources(sourceStateMap, sourceMap.values(), newSourceState, barcodesToUnstore);
        return opres;
    }

    // region Loading and validating

    /**
     * Checks for problems with lot numbers.
     * Lot numbers are optional, but their format is prescribed.
     * @param problems receptacle for problems
     * @param destinations the request destinations
     */
    public void validateLotNumbers(Collection<String> problems, Collection<SlotCopyDestination> destinations) {
        for (SlotCopyDestination destination : destinations) {
            if (!nullOrEmpty(destination.getLotNumber())) {
                lotNumberValidator.validate(destination.getLotNumber(), problems::add);
            }
            if (!nullOrEmpty(destination.getProbeLotNumber())) {
                lotNumberValidator.validate(destination.getProbeLotNumber(), problems::add);
            }
        }
    }

    /**
     * Loads labware specified as existing labware to put further samples into
     * @param problems receptacle for problems
     * @param opType the operation type
     * @param destinations the request destinations
     * @return the specified labware
     */
    public UCMap<Labware> loadExistingDestinations(Collection<String> problems, OperationType opType,
                                                   List<SlotCopyDestination> destinations) {
        List<String> barcodes = destinations.stream()
                .map(SlotCopyDestination::getBarcode)
                .filter(s -> !nullOrEmpty(s))
                .collect(toList());
        if (barcodes.isEmpty()) {
            return new UCMap<>(0); // none
        }
        if (opType != null && !opType.supportsActiveDest()) {
            problems.add("Reusing existing destinations is not supported for operation type "+opType.getName()+".");
        }
        LabwareValidator val = labwareValidatorFactory.getValidator();
        val.setUniqueRequired(true);
        val.loadLabware(lwRepo, barcodes);
        val.validateActiveDestinations();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Checks that the information is correct if given for existing destination labware
     * @param problems receptacle for problems
     * @param destinations the request destinations
     * @param labware map of labware from barcode
     * @param lwTypes map of labware type from name
     * @param bs map of bio states from name
     */
    public void checkExistingDestinations(Collection<String> problems, List<SlotCopyDestination> destinations,
                                               UCMap<Labware> labware, UCMap<LabwareType> lwTypes, UCMap<BioState> bs) {
        for (SlotCopyDestination dest : destinations) {
            if (nullOrEmpty(dest.getBarcode())) {
                continue;
            }
            Labware lw = labware.get(dest.getBarcode());
            if (lw==null) {
                continue;
            }
            checkExistingLabwareType(problems, lw, lwTypes.get(dest.getLabwareType()));
            checkExistingLabwareBioState(problems, lw, bs.get(dest.getBioState()));
        }
    }

    /**
     * Checks that specified labware type matches existing labware
     * @param problems receptacle for problems
     * @param lw existing labware, if any
     * @param lt specified labware type, if any
     */
    public void checkExistingLabwareType(Collection<String> problems, Labware lw, LabwareType lt) {
        if (lw!=null && lt!=null && !lw.getLabwareType().equals(lt)) {
            problems.add(String.format("Labware type %s specified for labware %s but it already has type %s.",
                    lt.getName(), lw.getBarcode(), lw.getLabwareType().getName()));
        }
    }

    /**
     * Checks that specified bio state matches existing labware
     * @param problems receptacle for problems
     * @param lw existing labware, if any
     * @param newBs specified bio state, if any
     */
    public void checkExistingLabwareBioState(Collection<String> problems, Labware lw, BioState newBs) {
        if (lw!=null && newBs!=null) {
            Set<BioState> lwBs = lw.getSlots().stream()
                    .flatMap(slot -> slot.getSamples().stream().map(Sample::getBioState))
                    .collect(toSet());
            if (lwBs.size() == 1) {
                BioState oldBs = lwBs.iterator().next();
                if (!oldBs.equals(newBs)) {
                    problems.add(String.format("Bio state %s specified for labware %s, which already uses bio state %s.",
                            newBs.getName(), lw.getBarcode(), oldBs.getName()));
                }
            }
        }
    }

    /**
     * Loads the labware types specified for destinations
     * @param problems receptacle for problems
     * @param destinations the destinations specified in the request
     * @return a map of barcodes to labware
     */
    public UCMap<LabwareType> loadLabwareTypes(Collection<String> problems, List<SlotCopyDestination> destinations) {
        boolean anyMissing = false;
        Set<String> lwTypeNames = new HashSet<>(destinations.size());
        for (SlotCopyDestination dest : destinations) {
            String name = dest.getLabwareType();
            if (nullOrEmpty(name)) {
                if (nullOrEmpty(dest.getBarcode())) {
                    anyMissing = true;
                }
            } else {
                lwTypeNames.add(name);
            }
        }
        if (anyMissing) {
            problems.add("Labware type name missing from request.");
        }
        if (lwTypeNames.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<LabwareType> lwTypes = UCMap.from(lwTypeRepo.findAllByNameIn(lwTypeNames), LabwareType::getName);
        List<String> missing = destinations.stream()
                .map(SlotCopyDestination::getLabwareType)
                .filter(s -> s!=null && !s.isEmpty() && lwTypes.get(s)==null)
                .distinct()
                .map(BasicUtils::repr)
                .collect(toList());
        if (!missing.isEmpty()) {
            problems.add("Unknown labware types: "+missing);
        }
        return lwTypes;
    }

    /**
     * Helper to load an entity using a function that returns an optional.
     * If it cannot be loaded, a problem is added to {@code problems}, and this method returns null
     * @param problems the receptacle for problems
     * @param name the name of the thing to load
     * @param desc the name of the type of thing
     * @param loadFn the function that returns an {@code Optional<E>}
     * @param <E> the type of thing being loaded
     * @return the thing loaded, or null if it could not be loaded
     */
    public <E> E loadEntity(Collection<String> problems, String name, String desc, Function<String, Optional<E>> loadFn) {
        if (name==null || name.isEmpty()) {
            problems.add("No "+desc+" specified.");
            return null;
        }
        var opt = loadFn.apply(name);
        if (opt.isEmpty()) {
            problems.add("Unknown "+desc+": "+repr(name));
            return null;
        }
        return opt.get();
    }

    /**
     * Checks the format and presence of the prebarcodes, if any
     * @param problems receptacle for problems found
     * @param destinations the specification of destinations
     * @param lwTypes the labware type specified in the request (if any)
     */
    public void checkPreBarcodes(Collection<String> problems, List<SlotCopyDestination> destinations, UCMap<LabwareType> lwTypes) {
        Set<String> missing = new LinkedHashSet<>();
        Set<String> unexpected = new LinkedHashSet<>();
        for (SlotCopyDestination dest : destinations) {
            String barcode = dest.getPreBarcode();
            LabwareType lt = lwTypes.get(dest.getLabwareType());
            if (barcode==null || barcode.isEmpty()) {
                if (lt != null && lt.isPrebarcoded()) {
                    missing.add(lt.getName());
                }
            } else if (lt != null && !lt.isPrebarcoded()) {
                unexpected.add(lt.getName());
            } else {
                preBarcodeValidator.validate(barcode.toUpperCase(), problems::add);
            }
        }
        if (!missing.isEmpty()) {
            problems.add("Expected a prebarcode for labware type: "+missing);
        }
        if (!unexpected.isEmpty()) {
            problems.add("Prebarcode not expected for labware type: "+unexpected);
        }
    }

    /**
     * Checks the prebarcodes are available for use: that means they must be unique in this request,
     * and not already used as a regular or external labware barcode.
     * @param problems receptacle for problems
     * @param destinations the requested destinations
     * @param existingDestinations the existing labware destinations
     */
    public void checkPreBarcodesInUse(Collection<String> problems, List<SlotCopyDestination> destinations,
                                      UCMap<Labware> existingDestinations) {
        Set<String> seen = new HashSet<>(destinations.size());
        for (SlotCopyDestination dest : destinations) {
            String prebc = dest.getPreBarcode();
            if (nullOrEmpty(prebc)) {
                continue;
            }
            prebc = prebc.toUpperCase();
            Labware existingDest = existingDestinations.get(dest.getBarcode());
            if (existingDest!=null) {
                if (!prebc.equalsIgnoreCase(existingDest.getExternalBarcode())
                    && !prebc.equalsIgnoreCase(existingDest.getBarcode())) {
                    problems.add(String.format("External barcode %s cannot be added to existing labware %s.",
                            repr(prebc), existingDest.getBarcode()));
                }
            } else if (!seen.add(prebc)) {
                problems.add("External barcode given multiple times: "+prebc);
            } else {
                if (lwRepo.existsByBarcode(prebc)) {
                    problems.add("Labware already exists with barcode "+prebc+".");
                } else if (lwRepo.existsByExternalBarcode(prebc)) {
                    problems.add("Labware already exists with external barcode "+prebc+".");
                }
            }
        }
    }

    /**
     * Loads the specified source labware into a {@code UCMap} from labware barcode.
     * @param problems receptacle for problems
     * @param request the request
     * @return a map of labware from its barcode
     */
    public UCMap<Labware> loadSources(Collection<String> problems, SlotCopyRequest request) {
        Set<String> sourceBarcodes = new HashSet<>();
        for (SlotCopyDestination dest : request.getDestinations()) {
            for (SlotCopyContent content : dest.getContents()) {
                String barcode = content.getSourceBarcode();
                if (barcode == null || barcode.isEmpty()) {
                    problems.add("Missing source barcode.");
                    continue;
                }
                sourceBarcodes.add(barcode.toUpperCase());
            }
        }
        if (sourceBarcodes.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<Labware> lwMap = UCMap.from(lwRepo.findByBarcodeIn(sourceBarcodes), Labware::getBarcode);
        List<String> unknownBarcodes = sourceBarcodes.stream()
                .filter(bc -> lwMap.get(bc)==null)
                .collect(toList());
        if (!unknownBarcodes.isEmpty()) {
            problems.add(pluralise("Unknown source barcode{s}: ", unknownBarcodes.size())
                    + reprCollection(unknownBarcodes));
        }
        return lwMap;
    }

    /**
     * Checks the sources and states specified in the request are reasonable.
     * @param problems receptacle for problems
     * @param request the request
     * @return a map of labware barcode to its new state
     */
    public UCMap<Labware.State> checkListedSources(Collection<String> problems, SlotCopyRequest request) {
        UCMap<Labware.State> bcStates = new UCMap<>(request.getSources().size());
        if (request.getSources().isEmpty()) {
            return bcStates;
        }
        Set<Labware.State> allowedStates = EnumSet.of(Labware.State.active, Labware.State.discarded, Labware.State.used);
        Set<String> usedSourceBarcodes = request.getDestinations().stream()
                .flatMap(dest -> dest.getContents().stream())
                .map(SlotCopyContent::getSourceBarcode)
                .filter(bc -> bc!=null && !bc.isEmpty())
                .map(String::toUpperCase)
                .collect(toSet());
        for (var src : request.getSources()) {
            String bc = src.getBarcode();
            if (bc==null || bc.isEmpty()) {
                problems.add("Source specified without barcode.");
            } else if (bcStates.containsKey(bc)) {
                problems.add("Repeated source barcode: "+bc);
            } else if (src.getLabwareState()==null) {
                problems.add("Source given without labware state: " + bc);
            } else if (!allowedStates.contains(src.getLabwareState())) {
                problems.add("Unsupported new labware state: "+src.getLabwareState());
            } else {
                bcStates.put(bc, src.getLabwareState());
            }
        }
        Set<String> unexpectedSourceBarcodes = bcStates.keySet()
                .stream()
                .filter(bc -> !usedSourceBarcodes.contains(bc))
                .collect(toSet());
        if (!unexpectedSourceBarcodes.isEmpty()) {
            problems.add("Source barcodes specified that do not map to any destination slots: "+unexpectedSourceBarcodes);
        }
        return bcStates;
    }

    /**
     * Validate some specifics for the ops being requested
     * @param problems receptacle for problems
     * @param dests the requested destinations
     * @param opType the operation type
     * @param lwTypes map to get lw types by name
     */
    public void validateOps(Collection<String> problems, List<SlotCopyDestination> dests, OperationType opType,
                            UCMap<LabwareType> lwTypes) {
        if (opType!=null && opType.getName().equalsIgnoreCase(CYTASSIST_OP)) {
            for (SlotCopyDestination dest : dests) {
                validateCytOp(problems, dest.getContents(), lwTypes.get(dest.getLabwareType()));
            }
        }
    }

    /**
     * Validates some things specific to the cytassist operation
     * @param problems receptacle for problems
     * @param contents the transfer contents specified in the request
     * @param lwType the labware type for this destination
     */
    public void validateCytOp(Collection<String> problems, Collection<SlotCopyContent> contents, LabwareType lwType) {
        if (lwType != null) {
            if (!lwType.getName().equalsIgnoreCase(CYTASSIST_SLIDE) && !lwType.getName().equalsIgnoreCase(CYTASSIST_SLIDE_XL)) {
                problems.add(String.format("Expected labware type %s or %s for operation %s.",
                        CYTASSIST_SLIDE, CYTASSIST_SLIDE_XL, CYTASSIST_OP));
            }
            if (lwType.getName().equalsIgnoreCase(CYTASSIST_SLIDE) && contents != null && !contents.isEmpty()) {
                for (SlotCopyContent content : contents) {
                    Address ad = content.getDestinationAddress();
                    if (ad != null && ad.getColumn()==1 && ad.getRow() > 1 && ad.getRow() < 4) {
                        problems.add("Slots B1 and C1 are disallowed for use in this operation.");
                        break;
                    }
                }
            }
        }
    }

    /**
     * Validates that the source labware is usable using {@link LabwareValidator}.
     * @param problems the receptacle for problems
     * @param labware the source labware being validated
     */
    public void validateSources(Collection<String> problems, Collection<Labware> labware) {
        LabwareValidator validator = labwareValidatorFactory.getValidator(labware);
        validator.setUniqueRequired(false);
        validator.setSingleSample(false);
        validator.validateSources();
        problems.addAll(validator.getErrors());
    }

    /**
     * Validates that the instructions of what to copy are valid for the source labware and new labware type
     * @param problems the receptacle for problems
     * @param lwTypes the types of the destination labware (values be null if a valid labware type was not specified)
     * @param lwMap the map of source barcode to labware (some labware may be missing if there were invalid/missing barcodes)
     * @param existingDestinations map of existing labware to add samples to
     * @param request the request
     */
    public void validateContents(Collection<String> problems, UCMap<LabwareType> lwTypes, UCMap<Labware> lwMap,
                                 UCMap<Labware> existingDestinations, SlotCopyRequest request) {
        if (request.getDestinations().isEmpty()) {
            problems.add("No destinations specified.");
            return;
        }
        if (request.getDestinations().stream()
                .anyMatch(dest -> dest==null || dest.getContents()==null || dest.getContents().isEmpty())) {
            problems.add("No contents specified in destination.");
        }
        for (var dest : request.getDestinations()) {
            Set<SlotCopyContent> contentSet = new HashSet<>(dest.getContents().size());
            Labware destLw = existingDestinations.get(dest.getBarcode());
            LabwareType lt = destLw != null ? destLw.getLabwareType() : lwTypes.get(dest.getLabwareType());
            for (var content : dest.getContents()) {
                Address destAddress = content.getDestinationAddress();
                if (!contentSet.add(content)) {
                    problems.add("Repeated copy specified: "+content);
                    continue;
                }
                if (destAddress == null) {
                    problems.add("No destination address specified.");
                } else if (destLw != null) {
                    Slot slot = destLw.optSlot(destAddress).orElse(null);
                    if (slot==null) {
                        problems.add(String.format("No such slot %s in labware %s.", destAddress, destLw.getBarcode()));
                    } else if (!slot.getSamples().isEmpty()) {
                        problems.add(String.format("Slot %s in labware %s is not empty.", destAddress, destLw.getBarcode()));
                    }
                } else if (lt != null && lt.indexOf(destAddress) < 0) {
                    problems.add("Invalid address " + destAddress + " for labware type " + lt.getName() + ".");
                }
                Address sourceAddress = content.getSourceAddress();
                Labware lw = lwMap.get(content.getSourceBarcode());
                if (sourceAddress == null) {
                    problems.add("No source address specified.");
                } else if (lw != null && lw.getLabwareType().indexOf(sourceAddress) < 0) {
                    problems.add("Invalid address " + sourceAddress + " for source labware " + lw.getBarcode() + ".");
                } else if (lw != null && lw.getSlot(sourceAddress).getSamples().isEmpty()) {
                    problems.add(String.format("Slot %s in labware %s is empty.", sourceAddress, lw.getBarcode()));
                }
            }
        }
    }

    public UCMap<BioState> validateBioStates(Collection<String> problems, Collection<SlotCopyDestination> dests) {
        Set<String> bsNames = new HashSet<>();
        for (SlotCopyDestination dest : dests) {
            if (dest!=null && dest.getBioState()!=null) {
                bsNames.add(dest.getBioState());
            }
        }
        if (bsNames.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<BioState> bioStates = UCMap.from(bsRepo.findAllByNameIn(bsNames), BioState::getName);
        Set<String> unknown = bsNames.stream()
                .filter(name -> !bioStates.containsKey(name))
                .collect(toSet());
        if (!unknown.isEmpty()) {
            problems.add("Unknown bio state: "+unknown);
        }
        Set<String> wrongBs = bioStates.values().stream()
                .map(BioState::getName)
                .filter(name -> !VALID_BS_UPPER.contains(name.toUpperCase()))
                .collect(toSet());
        if (!wrongBs.isEmpty()) {
            problems.add("Bio state not allowed for this operation: "+wrongBs);
        }
        return bioStates;
    }
    // endregion

    // region Executing

    public OperationResult executeOps(User user, Collection<SlotCopyDestination> dests,
                                      OperationType opType, UCMap<LabwareType> lwTypes, UCMap<BioState> bioStates,
                                      UCMap<Labware> sources, Work work, UCMap<Labware> existingDests) {
        List<Operation> ops = new ArrayList<>(dests.size());
        List<Labware> destLabware = new ArrayList<>(dests.size());
        for (SlotCopyDestination dest : dests) {
            OperationResult opres = executeOp(user, dest.getContents(), opType, lwTypes.get(dest.getLabwareType()),
                    dest.getPreBarcode(), sources, dest.getCosting(), dest.getLotNumber(), dest.getProbeLotNumber(),
                    bioStates.get(dest.getBioState()), existingDests.get(dest.getBarcode()));
            ops.addAll(opres.getOperations());
            destLabware.addAll(opres.getLabware());
        }
        if (work != null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, destLabware);
    }

    /**
     * Executes the request. Creates the new labware, records the operation, populates the labware,
     * creates new samples if necessary, discards sources if necessary.
     * This is called after successful validation, so it is expected to succeed.
     * @param user the user responsible for the operation
     * @param contents the description of what locations are copied to where
     * @param opType the type of operation being performed
     * @param lwType the type of labware to create
     * @param preBarcode the prebarcode of the new labware, if it has one
     * @param labwareMap a map of the source labware from their barcodes
     * @param costing the costing of new labware, if specified
     * @param lotNumber the lot number of the new labware, if specified
     * @param probeLotNumber the transcriptome probe lot number, if specified
     * @param bioState the new bio state of the labware, if given
     * @param destLw existing destination labware, if applicable
     * @return the result of the operation
     */
    public OperationResult executeOp(User user, Collection<SlotCopyContent> contents,
                                     OperationType opType, LabwareType lwType, String preBarcode,
                                     UCMap<Labware> labwareMap, SlideCosting costing, String lotNumber,
                                     String probeLotNumber, BioState bioState, Labware destLw) {
        if (destLw==null) {
            destLw = lwService.create(lwType, preBarcode, preBarcode);
        }
        Map<Integer, Sample> oldSampleIdToNewSample = createSamples(contents, labwareMap,
                coalesce(bioState, opType.getNewBioState()));
        Labware filledLabware = fillLabware(destLw, contents, labwareMap, oldSampleIdToNewSample);
        Operation op = createOperation(user, contents, opType, labwareMap, filledLabware, oldSampleIdToNewSample);
        if (costing != null) {
            lwNoteRepo.save(new LabwareNote(null, filledLabware.getId(), op.getId(), "costing", costing.name()));
        }
        if (!nullOrEmpty(lotNumber)) {
            lwNoteRepo.save(new LabwareNote(null, filledLabware.getId(), op.getId(), "lot", lotNumber.toUpperCase()));
        }
        if (!nullOrEmpty(probeLotNumber)) {
            lwNoteRepo.save(new LabwareNote(null, filledLabware.getId(), op.getId(), "probe lot", probeLotNumber.toUpperCase()));
        }
        return new OperationResult(List.of(op), List.of(filledLabware));
    }

    /**
     * Creates new samples as required for the operation.
     * Links each existing sample (via its id) to the sample that will represent it in the new labware.
     * If a bio state change is required, then the destination samples will be newly created.
     * Otherwise, the destination samples will be the source samples.
     * In either case, they will be entered into the map.
     * @param contents the description of what locations are copied to where
     * @param labwareMap the source labware, mapped from its barcode
     * @param newBioState the new bio state, or null if no new bio state is specified
     * @return a map of source sample id to destination sample (which may or may not be new)
     */
    public Map<Integer, Sample> createSamples(Collection<SlotCopyContent> contents, UCMap<Labware> labwareMap,
                                              BioState newBioState) {
        Map<Integer, Sample> map = new HashMap<>();
        for (var content: contents) {
            Labware lw = labwareMap.get(content.getSourceBarcode());
            Slot slot = lw.getSlot(content.getSourceAddress());
            for (Sample sample : slot.getSamples()) {
                Integer sampleId = sample.getId();
                if (map.get(sampleId)==null) {
                    if (newBioState==null || newBioState.equals(sample.getBioState())) {
                        map.put(sampleId, sample);
                    } else {
                        Sample newSample = sampleRepo.save(new Sample(null, sample.getSection(), sample.getTissue(), newBioState));
                        map.put(sampleId, newSample);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Puts samples into the destination labware
     * @param destLabware the destination labware
     * @param contents the description of what locations are copied to where
     * @param labwareMap the source labware, mapped from its barcode
     * @param oldSampleIdToNewSample the map of source sample id to destination sample
     * @return the populated labware
     */
    public Labware fillLabware(Labware destLabware, Collection<SlotCopyContent> contents, UCMap<Labware> labwareMap,
                               Map<Integer, Sample> oldSampleIdToNewSample) {
        for (var content: contents) {
            Slot src = labwareMap.get(content.getSourceBarcode()).getSlot(content.getSourceAddress());
            Slot dst = destLabware.getSlot(content.getDestinationAddress());
            for (Sample oldSample : src.getSamples()) {
                Sample newSample = oldSampleIdToNewSample.get(oldSample.getId());
                if (!dst.getSamples().contains(newSample)) {
                    dst.addSample(newSample);
                }
            }
        }
        List<Slot> slotsToSave = destLabware.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .collect(toList());
        slotRepo.saveAll(slotsToSave);
        entityManager.refresh(destLabware);
        return destLabware;
    }

    /**
     * Records the operation as requested
     * @param user the user responsible for the operation
     * @param contents the description of what locations are copied to where
     * @param opType the type of operation to record
     * @param labwareMap the source labware, mapped from its barcode
     * @param destLabware the new labware (containing the destination samples)
     * @param oldSampleIdToNewSample the map of source sample id to destination sample
     * @return the new operation
     */
    public Operation createOperation(User user, Collection<SlotCopyContent> contents, OperationType opType,
                                     UCMap<Labware> labwareMap, Labware destLabware, Map<Integer, Sample> oldSampleIdToNewSample) {
        List<Action> actions = new ArrayList<>();
        for (var content: contents) {
            Labware sourceLw = labwareMap.get(content.getSourceBarcode());
            Slot src = sourceLw.getSlot(content.getSourceAddress());
            Slot dest = destLabware.getSlot(content.getDestinationAddress());
            for (Sample sourceSample: src.getSamples()) {
                Sample destSample = oldSampleIdToNewSample.get(sourceSample.getId());
                Action action = new Action(null, null, src, dest, destSample, sourceSample);
                actions.add(action);
            }
        }
        return opService.createOperation(opType, user, actions, null);
    }

    /**
     * Update the lw state of source labware.
     * @param bcStateMap links of source barcode to explicit states
     * @param sources source labware
     * @param defaultState default new state (if any)
     * @param barcodesToUnstore receptacle to put barcodes that need to be unstored
     */
    public void updateSources(UCMap<Labware.State> bcStateMap, Collection<Labware> sources,
                              Labware.State defaultState, final Set<String> barcodesToUnstore) {
        final List<Labware> toUpdate = new ArrayList<>(sources.size());
        for (Labware lw : sources) {
            Labware.State newState = bcStateMap.getOrDefault(lw.getBarcode(), defaultState);
            if (newState==Labware.State.discarded && !lw.isDiscarded()) {
                lw.setDiscarded(true);
                barcodesToUnstore.add(lw.getBarcode());
                toUpdate.add(lw);
            } else if (newState==Labware.State.used && !lw.isUsed()) {
                lw.setUsed(true);
                toUpdate.add(lw);
            }
        }
        if (!toUpdate.isEmpty()) {
            lwRepo.saveAll(toUpdate);
        }
    }
    // endregion
}
