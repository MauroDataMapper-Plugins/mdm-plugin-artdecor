/*
 * Copyright 2020 University of Oxford
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


import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiBadRequestException
import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiInternalException
import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiUnauthorizedException
import uk.ac.ox.softeng.maurodatamapper.core.facet.Metadata
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataClass
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataElement
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.DataType
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.PrimitiveType
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.DataModelImporterProviderService
import uk.ac.ox.softeng.maurodatamapper.security.User

import java.nio.charset.Charset

@Slf4j
class ArtDecorDataModelImporterProviderService extends DataModelImporterProviderService<ArtDecorDataModelImporterProviderServiceParameters> {

    @Autowired
    DataModelService dataModelService

    @Override
    String getDisplayName() {
        'Art Decor Importer'
    }

    @Override
    String getVersion() {
        '2.1.0-SNAPSHOT'
    }

    @Override
    Boolean allowsExtraMetadataKeys() {
        true
    }

    @Override
    DataModel importModel(User user, ArtDecorDataModelImporterProviderServiceParameters t) {
        log.debug("importDataModel")
        importModels(user, t)?.first()
    }

    @Override
    List<DataModel> importModels(User currentUser, ArtDecorDataModelImporterProviderServiceParameters params) {
        if (!currentUser) throw new ApiUnauthorizedException('GLUEIP01', 'User must be logged in to import model')
        log.debug("importDataModels")
        FileParameter importFile = params.importFile
        if (!importFile.fileContents.size()) throw new ApiBadRequestException('EIS02', 'Cannot import empty file')

        log.info('Importing {} as {}', importFile.fileName, currentUser.emailAddress)

        importDataModels(currentUser, importFile.fileContents)
    }

    private List<DataModel> importDataModels(User currentUser, byte[] content) {
        if (!currentUser) throw new ApiUnauthorizedException('GLUEIP01', 'User must be logged in to import model')
        log.debug('Parsing in file content using JsonSlurper')
        def result = new JsonSlurper().parseText(new String(content, Charset.defaultCharset()))
        def datasets = result.datasets

        String namespace = "org.artdecor"

        List<DataModel> imported = []
        try {
            datasets.each { dataset ->
                def datasetList = dataset.dataset
                log.debug("importDataModel ${datasetList.name.get(0).content}")
                DataModel dataModel = new DataModel(label: datasetList.name.get(0).content)

                //Add metadata
                datasetList.each { dataMap ->
                    dataMap.each {
                        if (it.key != 'concept'
                                && it.key != 'desc'
                                && it.key != 'name') {
                            dataModel.addToMetadata(new Metadata(namespace: namespace, key: it.key, value: it.value))
                        }
                    }
                    Set<String> labels = new HashSet<>()
                    def conceptList = dataMap.concept
                    DataClass dataClass = new DataClass(label: datasetList.type)
                    if (conceptList) {
                        conceptList.each {
                            if (it.type == 'group') {

                                dataClass.label = it.name.get(0).content
                                dataClass.description = it.desc.get(0).content
                                dataClass.maxMultiplicity = it.maximumMultiplicity

                                def elementList = it.concept

                                it.entrySet().collect { e ->
                                    if (notExcludedProperties(e.key)){
                                        dataClass.addToMetadata(new Metadata(namespace: namespace, key: e.key, value: e.value))
                                    }
                                    if (e.key == 'concept') {

                                        elementList.each {
                                            if (it.type == 'item') {
                                                DataElement dataElement = new DataElement()
                                                DataType itemDataType = new PrimitiveType()
                                                String uniqueName = it.name.get(0).content
                                                dataElement.dataType = itemDataType
                                                dataElement.description = it.desc.get(0).content
                                                dataElement.maxMultiplicity = it.maximumMultiplicity

                                                it.entrySet().collect { el ->
                                                    if (notExcludedProperties(el.key)){
                                                        dataElement.addToMetadata(new Metadata(namespace: namespace, key: el.key, value: el.value))
                                                    }
                                                }
                                                if ( !labels.contains(uniqueName)) {
                                                    itemDataType.label = uniqueName
                                                    dataModel.addToDataTypes(itemDataType)
                                                    dataElement.label = uniqueName
                                                    dataClass.addToDataElements(dataElement)
                                                    labels.add(uniqueName)
                                                }

                                            }

                                        }

                                    }
                                }
                                dataModel.addToDataClasses(dataClass)
                            }
                        }
                    }
                }
                dataModelService.checkImportedDataModelAssociations(currentUser, dataModel)
                imported += dataModel

            }
        } catch (Exception ex) {
            throw new ApiInternalException('ART02', "${ex.message}")
        }
        imported
    }

    private boolean notExcludedProperties(String key) {
        return (key != 'implementation' && key != 'concept'
                && key != 'context'
                && key != 'comment'
                && key != 'source'
                && key != 'rationale'
                && key != 'property'
                && key != 'valueSet'
                && key != 'desc'
                && key != 'relationship'
                && key != 'name'
                && key != 'operationalization'
                && key != 'valueDomain')
    }

    DataModel updateImportedModelFromParameters(DataModel dataModel, ArtDecorDataModelImporterProviderServiceParameters params, boolean list = false) {
        if (params.finalised != null) dataModel.finalised = params.finalised
        if (!list && params.modelName) dataModel.label = params.modelName
        dataModel
    }

    @Override
    Boolean canImportMultipleDomains() {
        return null
    }
}
