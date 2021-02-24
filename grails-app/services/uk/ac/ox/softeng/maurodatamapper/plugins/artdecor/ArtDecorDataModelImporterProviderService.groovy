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
import uk.ac.ox.softeng.maurodatamapper.plugins.artdecor.provider.importer.parameter.ArtDecorDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.security.User

import grails.core.GrailsApplication
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.nio.charset.Charset

@Slf4j
class ArtDecorDataModelImporterProviderService extends DataModelImporterProviderService<ArtDecorDataModelImporterProviderServiceParameters> {

    DataModelService dataModelService
    GrailsApplication grailsApplication

    @Override
    String getDisplayName() {
        'Art Decor Importer'
    }

    @Override
    String getVersion() {
        getClass().getPackage().getSpecificationVersion() ?: 'SNAPSHOT'
    }

    @Override
    Boolean allowsExtraMetadataKeys() {
        true
    }

    @Override
    DataModel importModel(User user, ArtDecorDataModelImporterProviderServiceParameters params) {
        log.debug('Import model')
        importModels(user, params)?.first()
    }

    @Override
    List<DataModel> importModels(User currentUser, ArtDecorDataModelImporterProviderServiceParameters params) {
        if (!currentUser) throw new ApiUnauthorizedException('ART01', 'User must be logged in to import model')
        log.debug('Import Models')
        FileParameter importFile = params.importFile
        if (!importFile.fileContents.size()) throw new ApiBadRequestException('ART02', 'Cannot import empty file')

        log.info('Importing {} as {}', importFile.fileName, currentUser.emailAddress)

        log.debug('Parsing in file content using JsonSlurper')
        def result = new JsonSlurper().parseText(new String(importFile.fileContents, Charset.defaultCharset()))
        def datasets = result.datasets
        def dataset = result.dataset

        String namespace = "org.artdecor"

        List<DataModel> imported = []
        try {
            if (datasets) {
                imported = processMultiDatasets(currentUser, datasets, namespace, imported)
            } else if (dataset) {
                imported = processSingleDataset(currentUser, dataset, namespace, imported)
            }
        } catch (Exception ex) {
            throw new ApiInternalException('ART02', "Could not import ArtDecor models", ex)
        }
        imported
    }

    private List<DataModel> processMultiDatasets(User currentUser, datasets, String namespace, List<DataModel> imported) {
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
                        dataModel.addToMetadata(new Metadata(namespace: namespace, key: it.key, value: it.value.toString()))
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
                                dataClass.addToMetadata(new Metadata(namespace: namespace, key: e.key, value: e.value.toString()))
                                if (e.key == 'concept') {

                                    elementList.each {
                                        if (it.type == 'item') {
                                            DataElement dataElement = new DataElement()
                                            DataType itemDataType = new PrimitiveType()
                                            String uniqueName = it.name.get(0).content
                                            dataElement.dataType = itemDataType
                                            dataElement.description = it.desc.get(0).content
                                            dataElement.minMultiplicity = it.maximumMultiplicity
                                            dataElement.maxMultiplicity = it.maximumMultiplicity

                                            it.entrySet().collect { el ->
                                                dataElement.addToMetadata(new Metadata(namespace: namespace, key: el.key, value: el.value.toString()))
                                            }
                                            if (!labels.contains(uniqueName)) {
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
        imported
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

    List<DataModel> processSingleDataset(User currentUser, dataset, String namespace, List<DataModel> imported) {
        log.debug("importDataModel ${dataset.name.get(0).content}")
        DataModel dataModel = new DataModel(label: dataset.name.get(0).get(0).get("#text"))
        Set<String> labels = new HashSet<>()
        dataset.each { dataMap ->
            dataMap.each {
                if (it.key != 'concept'
                        && it.key != 'desc'
                        && it.key != 'name') {
                    dataModel.addToMetadata(new Metadata(namespace: namespace, key: it.key, value: it.value.toString()))
                }
            }
            List<Map<String, Object>> conceptList = dataMap.concept
            if (conceptList) {
                DataClass dataClass = new DataClass(label: dataset.type)
                conceptList.each { concept ->
                    if ( concept.type == 'group') {
                        processDataClass(concept as Map<String, Object>, dataModel, namespace, labels)
                    } else {
                        processElements(concept as Map<String, Object>, dataClass, namespace, dataModel, labels)
                        dataModel.addToDataClasses(dataClass)
                    }
                }

            }
            dataModelService.checkImportedDataModelAssociations(currentUser, dataModel)
            imported += dataModel

        }
        imported
    }

    private void processDataClass(Map<String,Object> it, DataModel dataModel, String namespace, Set<String> labels) {
        DataClass dataClass = new DataClass(label: it.type)
        String uniqueName = ((Map) ((List) it.name)[0])["#text"]
        if (!labels.contains(uniqueName)) {
            dataClass.label = ((Map) ((List) it.name)[0])["#text"]
            dataClass.description = ((Map) ((List) it.desc)[0])["#text"]
            dataClass.maxMultiplicity = parseInt(it.maximumMultiplicity as String)
            processElements(it, dataClass, namespace, dataModel, labels)
            processMetadata(it, null, dataClass)
            dataModel.addToDataClasses(dataClass)
        }

    }

    private void processElements(Map<String, Object> it, dataClass, String namespace, dataModel, Set<String> labels) {
        List<Map<String, Object>> elementList = it.concept
        it.entrySet().collect { e ->
            dataClass.addToMetadata(new Metadata(namespace: namespace, key: e.key, value: e.value.toString()))
            if (e.key == 'concept') {
                elementList.each {
                    if (it.type == 'item') {
                        DataElement dataElement = new DataElement()
                        DataType itemDataType = new PrimitiveType()
                        String uniqueName = ((Map) ((List) it.name)[0])["#text"]
                        dataElement.dataType = itemDataType
                        dataElement.description = ((Map) ((List) it.desc)[0])["#text"]
                        dataElement.minMultiplicity = parseInt(it.maximumMultiplicity)
                        dataElement.maxMultiplicity = parseInt(it.maximumMultiplicity != "*" ? it.maximumMultiplicity : "100")

                        processMetadata(it, dataElement, null)
                        if (!labels.contains(uniqueName)) {
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
    }

    private void processMetadata(Map<String, Object> it, DataElement element, DataClass dataClass) {
        String namespace = "org.artdecor"
        it.entrySet().collect {el ->
            if (element) {
                element.addToMetadata(new Metadata(namespace: namespace, key: el.key, value: el.value.toString()))
            } else {
                dataClass.addToMetadata(new Metadata(namespace: namespace, key: el.key, value: el.value.toString()))
            }
        }
    }

    private static Integer parseInt(String value) {
        try {
            value.toInteger()
        } catch (NumberFormatException ex) {
            null
        }
    }
}
