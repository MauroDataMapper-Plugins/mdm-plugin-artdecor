/*
 * Copyright 2020 University of Oxford and Health and Social Care Information Centre, also known as NHS Digital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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
        fileParameter.setFileContents(loadTestFile('artdecor-test-multiple-concepts.json'))
        parameters.importFile = fileParameter

        given:
        art.dataModelService = dataModelService

        when:
        def dataModels = art.importModels(admin, parameters)

        then:
        1 * dataModelService._
        //dataModel
        assert(dataModels.get(0).label=='Core information standard')
        //dataClasses
        assert(dataModels.get(0).dataClasses.description.get(0)=='Details about documents related to the person. &#160;')
        assert(dataModels.get(0).dataClasses.label.get(0)=='Documents (including correspondence and images)')
        assert(dataModels.get(0).dataClasses.maxMultiplicity.get(0)==49)
        assert(dataModels.get(0).dataClasses.metadata.get(0).size()==15)
        //dataElements
        assert(dataModels.get(0).dataClasses.dataElements.get(0).size()==32)
        assert(dataModels.get(0).dataClasses.dataElements.get(0).label.get(0)=='Date of birth')
        assert(dataModels.get(0).dataClasses.dataElements.get(0).dataType.label.get(0)=='Date of birth')
    }

    def "verify single dataset dataModel"() {
        DataModelService dataModelService = Mock()
        ArtDecorDataModelImporterProviderService art = new ArtDecorDataModelImporterProviderService()
        def parameters = new ArtDecorDataModelImporterProviderServiceParameters()
        def fileParameter = new FileParameter()
        fileParameter.setFileContents(loadTestFile('artDecor-payload-2.json'))
        parameters.importFile = fileParameter

        given:
        art.dataModelService = dataModelService

        when:
        def dataModels = art.importModels(admin, parameters)

        then:
        1 * dataModelService._
        //dataModel
        assert(dataModels.get(0).label=='About me')
    }


    @Override
    String getResourcePath() {
        ''
    }
}
