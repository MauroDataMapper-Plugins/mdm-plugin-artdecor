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

        String namespace = 'org.artdecor'

        List<DataModel> imported = []
        try {
            if (datasets) {
                imported = processMultiDatasets(currentUser, datasets, namespace, imported)
            } else if (dataset) {
                imported = processSingleDataset(currentUser, dataset, namespace, imported)
            }
        } catch (Exception ex) {
            throw new ApiInternalException('ART02', 'Could not import ArtDecor models', ex)
        }
        imported
    }

    private List<DataModel> processMultiDatasets(User currentUser, datasets, String namespace, List<DataModel> imported) {
        List<DataModel> dataModels = new ArrayList<>()
        datasets.each { dataset ->
            def datasetList = dataset.dataset
            log.debug("importDataModel ${datasetList.name[0].content}")
            //          DataModel dataModel = new DataModel(label: datasetList.name[0].content)

            dataModels += processSingleDataset(currentUser, datasetList, namespace, imported)
        }
        dataModels
    }

    DataModel updateImportedModelFromParameters(DataModel dataModel, ArtDecorDataModelImporterProviderServiceParameters params, boolean list = false) {
        if (params.finalised != null) dataModel.finalised = params.finalised
        if (!list && params.modelName) dataModel.label = params.modelName
        dataModel
    }

    @Override
    Boolean canImportMultipleDomains() {
        return true
    }

    List<DataModel> processSingleDataset(User currentUser, dataset, String namespace, List<DataModel> imported) {
        def name = dataset.name[0].content
        if (name == null) {
            name = dataset.name[0][0]['#text']
        }
        log.debug("importDataModel ${name}")
        DataModel dataModel = new DataModel(label: name)
        Set<String> labels = new HashSet<>()
        Map<String, PrimitiveType> primitiveDataTypes = [:]
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
                //DataClass dataClass = new DataClass(label: dataset.type)
                conceptList.each { concept ->
                    if ( concept.type == 'group') {
                        DataClass dataClass = processDataClass(concept as Map<String, Object>, dataModel, namespace, labels, primitiveDataTypes)
                        dataModel.addToDataClasses(dataClass)
                    } else {
                        // We're hopefully not getting any elements at the top level
                        System.err.println("We're hopefully not getting any elements at the top level")
                        System.err.println(concept)
                        //processElements(concept as Map<String, Object>, dataClass, namespace, dataModel, labels, primitiveDataTypes)
                        //dataModel.addToDataClasses(dataClass)
                    }
                }

            }
            dataModelService.checkImportedDataModelAssociations(currentUser, dataModel)
            imported += dataModel

        }
        imported
    }

    private DataClass processDataClass(Map<String,Object> it, DataModel dataModel, String namespace, Set<String> labels, Map<String, PrimitiveType>
            primitiveDataTypes) {
        String uniqueName = ((Map) ((List) it.name)[0]).content
        String description = ((Map) ((List) it.desc)[0]).content
        if (uniqueName == null ) {
            uniqueName = ((Map) ((List) it.name)[0])['#text']
        }

        if (description == null ) {
            description = ((Map) ((List) it.desc)[0])['#text']
        }

        DataClass dataClass = new DataClass(label: it.type)
        if (!labels.contains(uniqueName)) {
            dataClass.label = uniqueName
            dataClass.description = description
            dataClass.minMultiplicity = parseInt(it.minimumMultiplicity)
            dataClass.maxMultiplicity = parseInt(it.maximumMultiplicity == '*' ? -1 : it.maximumMultiplicity)
            it.concept.each { concept ->
                if (concept.type == 'group') {
                    DataClass childDataClass = processDataClass(concept as Map<String, Object>, dataModel, namespace, labels, primitiveDataTypes)
                    dataClass.addToDataClasses(childDataClass)
                }
            }
            processElements(it, dataClass, namespace, dataModel, labels, primitiveDataTypes)
            processMetadata(it, null, dataClass)
        }
        return dataClass

    }

    private void processElements(Map<String, Object> it, dataClass, String namespace, dataModel, Set<String> labels,
                                 Map<String, PrimitiveType> primitiveTypeMap) {
        List<Map<String, Object>> elementList = it.concept
        it.entrySet().collect { e ->
            dataClass.addToMetadata(new Metadata(namespace: namespace, key: e.key, value: e.value.toString()))
            if (e.key == 'concept') {
                elementList.each {
                    String uniqueName = ((Map) ((List) it.name)[0]).content
                    if (uniqueName == null ) {
                        uniqueName = ((Map) ((List) it.name)[0])['#text']
                    }
                    String description = ((Map) ((List) it.desc)[0]).content

                    if (description == null ) {
                        description = ((Map) ((List) it.desc)[0])['#text']
                    }
                    if (it.type == 'item') {
                        DataElement dataElement = new DataElement()
                        String dataTypeName = it.valueDomain[0].type
                        if(primitiveTypeMap[dataTypeName]) {
                            dataElement.dataType = primitiveTypeMap[dataTypeName]
                        } else {
                            PrimitiveType newPrimitiveType = new PrimitiveType(label: dataTypeName)
                            primitiveTypeMap[dataTypeName] = newPrimitiveType
                            dataElement.dataType = newPrimitiveType
                            dataModel.addToDataTypes(newPrimitiveType)
                        }

                        dataElement.description = description
                        dataElement.minMultiplicity = parseInt(it.minimumMultiplicity)
                        dataElement.maxMultiplicity = parseInt(it.maximumMultiplicity == '*' ? -1 : it.maximumMultiplicity)

                        processMetadata(it, dataElement, null)
                        if (!labels.contains(uniqueName)) {
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
        String namespace = 'org.artdecor'
        it.entrySet().collect {el ->
            if (element) {
                element.addToMetadata(new Metadata(namespace: namespace, key: el.key, value: el.value.toString()))
            } else {
                dataClass.addToMetadata(new Metadata(namespace: namespace, key: el.key, value: el.value.toString()))
            }
        }
    }

    private static Integer parseInt(def value) {
        if(value instanceof Number) return value.toInteger()
        if(value instanceof String) {
            try {
                value.toInteger()
            } catch (NumberFormatException ignored) {
            }
        }
        null
    }
}
