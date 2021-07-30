package uk.ac.sanger.sccp.stan.service.sas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.repo.*;

@Service
public class SasServiceImp implements SasService {
    private final ProjectRepo projectRepo;
    private final CostCodeRepo costCodeRepo;
    private final SasNumberRepo sasRepo;
    private final SasEventService sasEventService;

    @Autowired
    public SasServiceImp(ProjectRepo projectRepo, CostCodeRepo costCodeRepo, SasNumberRepo sasRepo,
                         SasEventService sasEventService) {
        this.projectRepo = projectRepo;
        this.costCodeRepo = costCodeRepo;
        this.sasRepo = sasRepo;
        this.sasEventService = sasEventService;
    }

    @Override
    public SasNumber createSasNumber(User user, String prefix, String projectName, String costCode) {
        Project project = projectRepo.getByName(projectName);
        CostCode cc = costCodeRepo.getByCode(costCode);

        String sasNum = sasRepo.createNumber(prefix);
        SasNumber sas = sasRepo.save(new SasNumber(null, sasNum, project, cc, Status.active));
        sasEventService.recordEvent(user, sas, SasEvent.Type.create, null);
        return sas;
    }

    @Override
    public SasNumber updateStatus(User user, String sasNum, Status newStatus, Integer commentId) {
        SasNumber sas = sasRepo.getBySasNumber(sasNum);
        sasEventService.recordStatusChange(user, sas, newStatus, commentId);
        sas.setStatus(newStatus);
        return sasRepo.save(sas);
    }

}
