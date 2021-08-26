package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.StainType;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.StainRequest;

import java.util.List;

/**
 * Service for requests related to stains and stain types.
 */
public interface StainService {
    /**
     * Gets the current supported stain types.
     * @return a list of enabled stain types
     */
    List<StainType> getEnabledStainTypes();

    /**
     * Records stain operations
     * @param user the user responsible for the operations
     * @param request the specification of the stain
     * @return the result of the operations
     */
    OperationResult recordStain(User user, StainRequest request);
}
