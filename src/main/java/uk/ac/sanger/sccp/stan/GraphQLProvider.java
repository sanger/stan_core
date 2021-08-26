package uk.ac.sanger.sccp.stan;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * @author dr6
 */
@Component
public class GraphQLProvider {

    private GraphQL graphQL;

    final Transactor transactor;
    final GraphQLDataFetchers graphQLDataFetchers;
    final GraphQLMutation graphQLMutation;
    final GraphQLStore graphQLStore;

    @Autowired
    public GraphQLProvider(Transactor transactor,
                           GraphQLDataFetchers graphQLDataFetchers, GraphQLMutation graphQLMutation, GraphQLStore graphQLStore) {
        this.transactor = transactor;
        this.graphQLDataFetchers = graphQLDataFetchers;
        this.graphQLMutation = graphQLMutation;
        this.graphQLStore = graphQLStore;
    }

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @PostConstruct
    public void init() throws IOException {
        //noinspection UnstableApiUsage
        URL url = Resources.getResource("schema.graphqls");
        //noinspection UnstableApiUsage
        String sdl = Resources.toString(url, Charsets.UTF_8);
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.graphQL = GraphQL.newGraphQL(graphQLSchema)
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(new StanExceptionHandler()))
                .queryExecutionStrategy(new AsyncExecutionStrategy(new StanExceptionHandler()))
                .build();
    }

    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("user", graphQLDataFetchers.getUser())
                        .dataFetcher("tissueTypes", graphQLDataFetchers.getTissueTypes())
                        .dataFetcher("hmdmcs", graphQLDataFetchers.getHmdmcs())
                        .dataFetcher("labwareTypes", graphQLDataFetchers.getLabwareTypes())
                        .dataFetcher("mediums", graphQLDataFetchers.getMediums())
                        .dataFetcher("fixatives", graphQLDataFetchers.getFixatives())
                        .dataFetcher("mouldSizes", graphQLDataFetchers.getMouldSizes())
                        .dataFetcher("species", graphQLDataFetchers.getSpecies())
                        .dataFetcher("labware", graphQLDataFetchers.findLabwareByBarcode())
                        .dataFetcher("printers", graphQLDataFetchers.findPrinters())
                        .dataFetcher("comments", graphQLDataFetchers.getComments())
                        .dataFetcher("releaseDestinations", graphQLDataFetchers.getReleaseDestinations())
                        .dataFetcher("releaseRecipients", graphQLDataFetchers.getReleaseRecipients())
                        .dataFetcher("find", graphQLDataFetchers.find())
                        .dataFetcher("destructionReasons", graphQLDataFetchers.getDestructionReasons())
                        .dataFetcher("projects", graphQLDataFetchers.getProjects())
                        .dataFetcher("costCodes", graphQLDataFetchers.getCostCodes())
                        .dataFetcher("workTypes", graphQLDataFetchers.getWorkTypes())
                        .dataFetcher("works", graphQLDataFetchers.getWorks())
                        .dataFetcher("work", graphQLDataFetchers.getWork())
                        .dataFetcher("stainTypes", graphQLDataFetchers.getEnabledStainTypes())

                        .dataFetcher("users", graphQLDataFetchers.getUsers())
                        .dataFetcher("planData", graphQLDataFetchers.getPlanData())

                        .dataFetcher("historyForSampleId", graphQLDataFetchers.historyForSampleId())
                        .dataFetcher("historyForExternalName", graphQLDataFetchers.historyForExternalName())
                        .dataFetcher("historyForDonorName", graphQLDataFetchers.historyForDonorName())
                        .dataFetcher("historyForLabwareBarcode", graphQLDataFetchers.historyForLabwareBarcode())

                        .dataFetcher("location", graphQLStore.getLocation())
                        .dataFetcher("stored", graphQLStore.getStored())
                )
                .type(newTypeWiring("Mutation")
                        .dataFetcher("login", graphQLMutation.logIn())
                        .dataFetcher("logout", graphQLMutation.logOut())
                        .dataFetcher("register", transact(graphQLMutation.register()))
                        .dataFetcher("plan", transact(graphQLMutation.recordPlan()))
                        .dataFetcher("printLabware", graphQLMutation.printLabware()) // not transacted
                        .dataFetcher("confirmOperation", transact(graphQLMutation.confirmOperation()))
                        .dataFetcher("confirmSection", transact(graphQLMutation.confirmSection()))
                        .dataFetcher("release", graphQLMutation.release()) // transaction handled in service
                        .dataFetcher("extract", graphQLMutation.extract()) // transaction handled in service
                        .dataFetcher("destroy", graphQLMutation.destroy()) // transaction handled in service
                        .dataFetcher("registerSections", transact(graphQLMutation.sectionRegister()))
                        .dataFetcher("slotCopy", graphQLMutation.slotCopy()) // transaction handled in service

                        .dataFetcher("addComment", transact(graphQLMutation.addComment()))
                        .dataFetcher("setCommentEnabled", transact(graphQLMutation.setCommentEnabled()))
                        .dataFetcher("addHmdmc", transact(graphQLMutation.addHmdmc()))
                        .dataFetcher("setHmdmcEnabled", transact(graphQLMutation.setHmdmcEnabled()))
                        .dataFetcher("addDestructionReason", transact(graphQLMutation.addDestructionReason()))
                        .dataFetcher("setDestructionReasonEnabled", transact(graphQLMutation.setDestructionReasonEnabled()))
                        .dataFetcher("addReleaseDestination", transact(graphQLMutation.addReleaseDestination()))
                        .dataFetcher("setReleaseDestinationEnabled", transact(graphQLMutation.setReleaseDestinationEnabled()))
                        .dataFetcher("addReleaseRecipient", transact(graphQLMutation.addReleaseRecipient()))
                        .dataFetcher("setReleaseRecipientEnabled", transact(graphQLMutation.setReleaseRecipientEnabled()))
                        .dataFetcher("addSpecies", transact(graphQLMutation.addSpecies()))
                        .dataFetcher("setSpeciesEnabled", transact(graphQLMutation.setSpeciesEnabled()))
                        .dataFetcher("addProject", transact(graphQLMutation.addProject()))
                        .dataFetcher("setProjectEnabled", transact(graphQLMutation.setProjectEnabled()))
                        .dataFetcher("addCostCode", transact(graphQLMutation.addCostCode()))
                        .dataFetcher("setCostCodeEnabled", transact(graphQLMutation.setCostCodeEnabled()))
                        .dataFetcher("addWorkType", transact(graphQLMutation.addWorkType()))
                        .dataFetcher("setWorkTypeEnabled", transact(graphQLMutation.setWorkTypeEnabled()))
                        .dataFetcher("createWork", transact(graphQLMutation.createWork()))
                        .dataFetcher("updateWorkStatus", transact(graphQLMutation.updateWorkStatus()))
                        .dataFetcher("stain", transact(graphQLMutation.stain()))

                        .dataFetcher("addUser", transact(graphQLMutation.addUser()))
                        .dataFetcher("setUserRole", transact(graphQLMutation.setUserRole()))

                        .dataFetcher("storeBarcode", graphQLStore.storeBarcode())
                        .dataFetcher("unstoreBarcode", graphQLStore.unstoreBarcode())
                        .dataFetcher("empty", graphQLStore.empty())
                        .dataFetcher("setLocationCustomName", graphQLStore.setLocationCustomName())
                )
                .scalar(GraphQLCustomTypes.ADDRESS)
                .scalar(GraphQLCustomTypes.TIMESTAMP)
                .scalar(GraphQLCustomTypes.DATE)
                .build();
    }

    private <T> DataFetcher<T> transact(DataFetcher<T> dataFetcher) {
        return transactor.dataFetcher("Mutation transaction", dataFetcher);
    }
}