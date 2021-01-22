package uk.ac.ox.softeng.maurodatamapper.plugins.artdecor

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import grails.util.BuildSettings
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared
import spock.lang.Specification
import uk.ac.ox.softeng.maurodatamapper.core.bootstrap.StandardEmailAddress
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.security.User
import uk.ac.ox.softeng.maurodatamapper.test.functional.BaseFunctionalSpec
import uk.ac.ox.softeng.maurodatamapper.test.unit.security.TestUser

import javax.transaction.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
@Integration
class ArtDecorDataModelImporterProviderServiceSpec extends BaseFunctionalSpec {

    @Autowired
    DataModelService dataModelService

    @Shared
    Path resourcesPath

    @OnceBefore
    void setupResourcesPath() {
        resourcesPath = Paths.get(BuildSettings.BASE_DIR.absolutePath, 'src', 'integration-test', 'resources').toAbsolutePath()
    }


    byte[] loadTestFile(String filename) {
        Path testFilePath = resourcesPath.resolve("${filename}").toAbsolutePath()
        assert Files.exists(testFilePath)
        Files.readAllBytes(testFilePath)
    }

    User getAdmin() {
        new TestUser(emailAddress: StandardEmailAddress.ADMIN,
                firstName: 'Admin',
                lastName: 'User',
                organisation: 'Oxford BRC Informatics',
                jobTitle: 'God',
                id: UUID.randomUUID())
    }

    def "verify dataModel"() {
        given:
        ArtDecorDataModelImporterProviderService art = new ArtDecorDataModelImporterProviderService()
        def parameters = new ArtDecorDataModelImporterProviderServiceParameters()
        def fileParameter = new FileParameter()
        fileParameter.setFileContents(loadTestFile('artdecor-test.json'))
        parameters.importFile = fileParameter


        when:
        def dataModels = art.importDataModels(admin, parameters)

        then:
        dataModels
    }

    @Override
    String getResourcePath() {
        ''
    }
}
