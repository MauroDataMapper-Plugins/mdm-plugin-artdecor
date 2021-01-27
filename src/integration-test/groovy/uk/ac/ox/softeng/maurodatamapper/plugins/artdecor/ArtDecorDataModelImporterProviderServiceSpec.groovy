package uk.ac.ox.softeng.maurodatamapper.plugins.artdecor

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import grails.util.BuildSettings
import groovy.util.logging.Slf4j
import spock.lang.Shared
import uk.ac.ox.softeng.maurodatamapper.core.bootstrap.StandardEmailAddress
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.security.User
import uk.ac.ox.softeng.maurodatamapper.test.functional.BaseFunctionalSpec
import uk.ac.ox.softeng.maurodatamapper.test.unit.security.TestUser

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
@Integration
class ArtDecorDataModelImporterProviderServiceSpec extends BaseFunctionalSpec {

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
        DataModelService dataModelService = Mock()
        ArtDecorDataModelImporterProviderService art = new ArtDecorDataModelImporterProviderService()
        def parameters = new ArtDecorDataModelImporterProviderServiceParameters()
        def fileParameter = new FileParameter()
        fileParameter.setFileContents(loadTestFile('artdecor-test.json'))
        parameters.importFile = fileParameter

        given:
        art.dataModelService = dataModelService

        when:
        def dataModels = art.importDataModels(admin, parameters)

        then:
        1 * dataModelService._
        assert(dataModels.get(0).label=='Problem list')
        assert(dataModels.get(0).dataClasses.description.get(0)=='This is a problem list record entry.  There may be 0 to many record entries under problem list.  Each record entry is made up of a number of elements or data items. ')
        assert(dataModels.get(0).dataClasses.label.get(0)=='Problem list record entry')
        assert(dataModels.get(0).dataClasses.maxMultiplicity.get(0)==42)
    }

    @Override
    String getResourcePath() {
        ''
    }
}
