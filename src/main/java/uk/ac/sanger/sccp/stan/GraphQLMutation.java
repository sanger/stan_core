package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.request.plan.PlanResult;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.extract.ExtractService;
import uk.ac.sanger.sccp.stan.service.label.print.LabelPrintService;
import uk.ac.sanger.sccp.stan.service.operation.confirm.ConfirmOperationService;
import uk.ac.sanger.sccp.stan.service.operation.confirm.ConfirmSectionService;
import uk.ac.sanger.sccp.stan.service.operation.plan.PlanService;
import uk.ac.sanger.sccp.stan.service.register.RegisterService;
import uk.ac.sanger.sccp.stan.service.register.SectionRegisterService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.stan.service.work.WorkTypeService;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Component
public class GraphQLMutation extends BaseGraphQLResource {
    Logger log = LoggerFactory.getLogger(GraphQLMutation.class);
    final LDAPService ldapService;
    final SessionConfig sessionConfig;
    final RegisterService registerService;
    final SectionRegisterService sectionRegisterService;
    final PlanService planService;
    final LabelPrintService labelPrintService;
    final ConfirmOperationService confirmOperationService;
    final ConfirmSectionService confirmSectionService;
    final ReleaseService releaseService;
    final ExtractService extractService;
    final DestructionService destructionService;
    final SlotCopyService slotCopyService;
    final CommentAdminService commentAdminService;
    final DestructionReasonAdminService destructionReasonAdminService;
    final HmdmcAdminService hmdmcAdminService;
    final ReleaseDestinationAdminService releaseDestinationAdminService;
    final ReleaseRecipientAdminService releaseRecipientAdminService;
    final SpeciesAdminService speciesAdminService;
    final ProjectService projectService;
    final CostCodeService costCodeService;
    final FixativeService fixativeService;
    final WorkTypeService workTypeService;
    final WorkService workService;
    final UserAdminService userAdminService;

    @Autowired
    public GraphQLMutation(ObjectMapper objectMapper, AuthenticationComponent authComp,
                           LDAPService ldapService, SessionConfig sessionConfig,
                           RegisterService registerService, SectionRegisterService sectionRegisterService, PlanService planService,
                           LabelPrintService labelPrintService,
                           ConfirmOperationService confirmOperationService,
                           UserRepo userRepo, ConfirmSectionService confirmSectionService, ReleaseService releaseService, ExtractService extractService,
                           DestructionService destructionService, SlotCopyService slotCopyService,
                           CommentAdminService commentAdminService, DestructionReasonAdminService destructionReasonAdminService,
                           HmdmcAdminService hmdmcAdminService, ReleaseDestinationAdminService releaseDestinationAdminService,
                           ReleaseRecipientAdminService releaseRecipientAdminService, SpeciesAdminService speciesAdminService,
                           ProjectService projectService, CostCodeService costCodeService, FixativeService fixativeService,
                           WorkTypeService workTypeService,
                           WorkService workService,
                           UserAdminService userAdminService) {
        super(objectMapper, authComp, userRepo);
        this.ldapService = ldapService;
        this.sessionConfig = sessionConfig;
        this.registerService = registerService;
        this.sectionRegisterService = sectionRegisterService;
        this.planService = planService;
        this.labelPrintService = labelPrintService;
        this.confirmOperationService = confirmOperationService;
        this.confirmSectionService = confirmSectionService;
        this.releaseService = releaseService;
        this.extractService = extractService;
        this.destructionService = destructionService;
        this.slotCopyService = slotCopyService;
        this.commentAdminService = commentAdminService;
        this.destructionReasonAdminService = destructionReasonAdminService;
        this.hmdmcAdminService = hmdmcAdminService;
        this.releaseDestinationAdminService = releaseDestinationAdminService;
        this.releaseRecipientAdminService = releaseRecipientAdminService;
        this.speciesAdminService = speciesAdminService;
        this.projectService = projectService;
        this.costCodeService = costCodeService;
        this.fixativeService = fixativeService;
        this.workTypeService = workTypeService;
        this.workService = workService;
        this.userAdminService = userAdminService;
    }

    private void logRequest(String name, User user, Object request) {
        if (log.isInfoEnabled()) {
            log.info("{} requested by {}: {}", name, (user==null ? null : repr(user.getUsername())), request);
        }
    }

    public DataFetcher<LoginResult> logIn() {
        return dataFetchingEnvironment -> {
            String username = dataFetchingEnvironment.getArgument("username");
            if (log.isInfoEnabled()) {
                log.info("Login attempt by {}", repr(username));
            }
            Optional<User> optUser = userRepo.findByUsername(username);
            if (optUser.isEmpty()) {
                return new LoginResult("Username not in database.", null);
            }
            User user = optUser.get();
            if (user.getRole()==User.Role.disabled) {
                return new LoginResult("Username is disabled.", null);
            }
            String password = dataFetchingEnvironment.getArgument("password");
            if (!ldapService.verifyCredentials(username, password)) {
                return new LoginResult("Login failed", null);
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(user, password, new ArrayList<>());
            authComp.setAuthentication(authentication, sessionConfig.getMaxInactiveMinutes());
            log.info("Login succeeded for user {}", user);
            return new LoginResult("OK", user);
        };
    }

    private String loggedInUsername() {
        var auth = authComp.getAuthentication();
        if (auth != null) {
            var princ = auth.getPrincipal();
            if (princ instanceof User) {
                return ((User) princ).getUsername();
            }
        }
        return null;
    }

    public DataFetcher<String> logOut() {
        return dataFetchingEnvironment -> {
            if (log.isInfoEnabled()) {
                log.info("Logout requested by {}", repr(loggedInUsername()));
            }
            authComp.setAuthentication(null, 0);
            return "OK";
        };
    }

    public DataFetcher<RegisterResult> register() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            RegisterRequest request = arg(dfe, "request", RegisterRequest.class);
            logRequest("Register", user, request);
            return registerService.register(request, user);
        };
    }

    public DataFetcher<RegisterResult> sectionRegister() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            SectionRegisterRequest request = arg(dfe, "request", SectionRegisterRequest.class);
            logRequest("Section register", user, request);
            return sectionRegisterService.register(user, request);
        };
    }

    public DataFetcher<PlanResult> recordPlan() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            PlanRequest request = arg(dfe, "request", PlanRequest.class);
            logRequest("Record plan", user, request);
            return planService.recordPlan(user, request);
        };
    }

    public DataFetcher<String> printLabware() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            List<String> barcodes = dfe.getArgument("barcodes");
            String printerName = dfe.getArgument("printer");
            if (log.isInfoEnabled()) {
                logRequest("Print labware", user, "Printer: " + repr(printerName) + ", barcodes: " + barcodes);
            }
            labelPrintService.printLabwareBarcodes(user, printerName, barcodes);
            return "OK";
        };
    }

    public DataFetcher<ConfirmOperationResult> confirmOperation() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            ConfirmOperationRequest request = arg(dfe, "request", ConfirmOperationRequest.class);
            logRequest("Confirm operation", user, request);
            return confirmOperationService.confirmOperation(user, request);
        };
    }

    public DataFetcher<OperationResult> confirmSection() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            ConfirmSectionRequest request = arg(dfe, "request", ConfirmSectionRequest.class);
            logRequest("Confirm section", user, request);
            return confirmSectionService.confirmOperation(user, request);
        };
    }

    public DataFetcher<ReleaseResult> release() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            ReleaseRequest request = arg(dfe, "request", ReleaseRequest.class);
            logRequest("Release", user, request);
            return releaseService.releaseAndUnstore(user, request);
        };
    }

    public DataFetcher<OperationResult> extract() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            ExtractRequest request = arg(dfe, "request", ExtractRequest.class);
            logRequest("Extract", user, request);
            return extractService.extractAndUnstore(user, request);
        };
    }

    public DataFetcher<DestroyResult> destroy() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            DestroyRequest request = arg(dfe, "request", DestroyRequest.class);
            logRequest("Destroy", user, request);
            return destructionService.destroyAndUnstore(user, request);
        };
    }

    public DataFetcher<OperationResult> slotCopy() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            SlotCopyRequest request = arg(dfe, "request", SlotCopyRequest.class);
            logRequest("SlotCopy", user, request);
            return slotCopyService.perform(user, request);
        };
    }

    public DataFetcher<Comment> addComment() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.admin);
            String category = dfe.getArgument("category");
            String text = dfe.getArgument("text");
            logRequest("AddComment", user, String.format("(category=%s, text=%s)", repr(category), repr(text)));
            return commentAdminService.addComment(category, text);
        };
    }

    public DataFetcher<Comment> setCommentEnabled() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.admin);
            Integer commentId = dfe.getArgument("commentId");
            Boolean enabled = dfe.getArgument("enabled");
            requireNonNull(commentId, "commentId not specified");
            requireNonNull(enabled, "enabled not specified");
            logRequest("SetCommentEnabled", user, String.format("(commentId=%s, enabled=%s)", commentId, enabled));
            return commentAdminService.setCommentEnabled(commentId, enabled);
        };
    }

    public DataFetcher<DestructionReason> addDestructionReason() {
        return adminAdd(destructionReasonAdminService::addNew, "AddDestructionReason", "text");
    }

    public DataFetcher<DestructionReason> setDestructionReasonEnabled() {
        return adminSetEnabled(destructionReasonAdminService::setEnabled, "SetDestructionReasonEnabled", "text");
    }

    public DataFetcher<Hmdmc> addHmdmc() {
        return adminAdd(hmdmcAdminService::addNew, "AddHmdmc", "hmdmc");
    }

    public DataFetcher<Hmdmc> setHmdmcEnabled() {
        return adminSetEnabled(hmdmcAdminService::setEnabled, "SetHmdmcEnabled", "hmdmc");
    }

    public DataFetcher<ReleaseDestination> addReleaseDestination() {
        return adminAdd(releaseDestinationAdminService::addNew, "AddReleaseDestination", "name");
    }

    public DataFetcher<ReleaseDestination> setReleaseDestinationEnabled() {
        return adminSetEnabled(releaseDestinationAdminService::setEnabled, "SetReleaseDestinationEnabled", "name");
    }

    public DataFetcher<ReleaseRecipient> addReleaseRecipient() {
        return adminAdd(releaseRecipientAdminService::addNew, "AddReleaseRecipient", "username");
    }

    public DataFetcher<ReleaseRecipient> setReleaseRecipientEnabled() {
        return adminSetEnabled(releaseRecipientAdminService::setEnabled, "SetReleaseRecipientEnabled", "username");
    }

    public DataFetcher<Species> addSpecies() {
        return adminAdd(speciesAdminService::addNew, "AddSpecies", "name");
    }

    public DataFetcher<Species> setSpeciesEnabled() {
        return adminSetEnabled(speciesAdminService::setEnabled, "SetSpeciesEnabled", "name");
    }

    public DataFetcher<Project> addProject() {
        return adminAdd(projectService::addNew, "AddProject", "name");
    }

    public DataFetcher<Project> setProjectEnabled() {
        return adminSetEnabled(projectService::setEnabled, "SetProjectEnabled", "name");
    }

    public DataFetcher<CostCode> addCostCode() {
        return adminAdd(costCodeService::addNew, "AddCostCode", "code");
    }

    public DataFetcher<CostCode> setCostCodeEnabled() {
        return adminSetEnabled(costCodeService::setEnabled, "SetCostCodeEnabled", "code");
    }

    public DataFetcher<Fixative> addFixative() {
        return adminAdd(fixativeService::addNew, "AddFixative", "name");
    }

    public DataFetcher<Fixative> setFixativeEnabled() {
        return adminSetEnabled(fixativeService::setEnabled, "SetFixativeEnabled", "name");
    }

    public DataFetcher<WorkType> addWorkType() {
        return adminAdd(workTypeService::addNew, "AddWorkType", "name");
    }

    public DataFetcher<WorkType> setWorkTypeEnabled() {
        return adminSetEnabled(workTypeService::setEnabled, "SetWorkTypeEnabled", "name");
    }

    public DataFetcher<Work> createWork() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            String projectName = dfe.getArgument("project");
            String code = dfe.getArgument("costCode");
            String prefix = dfe.getArgument("prefix");
            String workTypeName = dfe.getArgument("workType");
            return workService.createWork(user, prefix, workTypeName, projectName, code);
        };
    }

    public DataFetcher<Work> updateWorkStatus() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.normal);
            String workNumber = dfe.getArgument("workNumber");
            Work.Status status = arg(dfe, "status", Work.Status.class);
            Integer commentId = dfe.getArgument("commentId");
            return workService.updateStatus(user, workNumber, status, commentId);
        };
    }

    public DataFetcher<User> addUser() {
        return adminAdd(userAdminService::addUser, "AddUser", "username");
    }

    public DataFetcher<User> setUserRole() {
        return dfe -> {
            User user = checkUser(dfe, User.Role.admin);
            String username = dfe.getArgument("username");
            User.Role role = arg(dfe, "role", User.Role.class);
            logRequest("SetUserRole", user, String.format("%s -> %s", repr(username), role));
            return userAdminService.setUserRole(username, role);
        };
    }

    private <E> DataFetcher<E> adminAdd(Function<String, E> addFunction, String functionName, String argName) {
        return dfe -> {
            User user = checkUser(dfe, User.Role.admin);
            String arg = dfe.getArgument(argName);
            logRequest(functionName, user, repr(arg));
            return addFunction.apply(arg);
        };
    }

    private <E> DataFetcher<E> adminSetEnabled(BiFunction<String, Boolean, E> setEnabledFunction, String functionName, String argName) {
        return dfe -> {
            User user = checkUser(dfe, User.Role.admin);
            String arg = dfe.getArgument(argName);
            Boolean enabled = dfe.getArgument("enabled");
            requireNonNull(enabled, "enabled not specified.");
            logRequest(functionName, user, String.format("arg: %s, enabled: %s", repr(arg), enabled));
            return setEnabledFunction.apply(arg, enabled);
        };
    }

}
