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


import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.plugins.artdecor.provider.importer.parameter.ArtDecorDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.test.integration.BaseIntegrationSpec

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import grails.util.BuildSettings
import groovy.util.logging.Slf4j
import spock.lang.Shared

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
@Integration
@Rollback
class ArtDecorDataModelImporterProviderServiceSpec extends BaseIntegrationSpec {

    DataModelService dataModelService

    ArtDecorDataModelImporterProviderService artDecorDataModelImporterProviderService

    @Shared
    Path resourcesPath

    @OnceBefore
    void setupResourcesPath() {
        resourcesPath = Paths.get(BuildSettings.BASE_DIR.absolutePath, 'src', 'integration-test', 'resources').toAbsolutePath()
    }

    @Override
    void setupDomainData() {
    }

    byte[] loadTestFile(String filename) {
        Path testFilePath = resourcesPath.resolve("${filename}").toAbsolutePath()
        assert Files.exists(testFilePath)
        Files.readAllBytes(testFilePath)
    }

    def "verify artdecor-test-multiple-concepts"() {
        def parameters = new ArtDecorDataModelImporterProviderServiceParameters(
            importFile: new FileParameter(fileContents: loadTestFile('artdecor-test-multiple-concepts.json'))
        )

        when:
        def dataModels = artDecorDataModelImporterProviderService.importModels(admin, parameters)

        then:
        dataModels.size() == 1
        !dataModels.first().id

        when:
        DataModel saved = dataModelService.saveModelWithContent(dataModels.first())

        then:
        //dataModel
        saved.id
        saved.label == 'Core information standard'
        //dataClasses
        saved.dataClasses.description[0] == "The person's details and contact information."
        saved.dataClasses[0].label== "Person demographics"
        saved.dataClasses[0].maxMultiplicity== 1
        saved.dataClasses[0].metadata.size()== 15
        //dataElements
        //You should absolutely define the dataclass or element you're hitting NOT the first thing in the sub list
        saved.dataClasses[0].dataElements.size()== 13
        saved.dataClasses[0].dataElements[0].label == 'Date of birth'
        saved.dataClasses[0].dataElements[0].dataType.label== 'date'
    }

    def "verify artDecor-payload-1"() {
        def parameters = new ArtDecorDataModelImporterProviderServiceParameters(
            importFile: new FileParameter(fileContents: loadTestFile('artDecor-payload-1.json'))
        )

        when:
        def dataModels = artDecorDataModelImporterProviderService.importModels(admin, parameters)

        then:
        dataModels[0].label == 'Local authority information'
    }


}
