package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Fixative;
import uk.ac.sanger.sccp.stan.repo.FixativeRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link FixativeService}
 * @author dr6
 */
public class TestFixativeService extends AdminServiceTestUtils<Fixative, FixativeRepo, FixativeService> {
    public TestFixativeService() {
        super("Fixative", Fixative::new,
                FixativeRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(FixativeRepo.class);
        service = new FixativeService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(FixativeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(FixativeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
