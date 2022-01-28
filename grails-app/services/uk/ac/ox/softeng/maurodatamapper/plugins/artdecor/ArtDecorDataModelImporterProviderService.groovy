/*
 * Copyright 2020-2022 University of Oxford and Health and Social Care Information Centre, also known as NHS Digital
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
import uk.ac.ox.softeng.maurodatamapper.core.model.CatalogueItem
import uk.ac.ox.softeng.maurodatamapper.core.provider.importer.parameter.FileParameter
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataClass
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.DataElement
import uk.ac.ox.softeng.maurodatamapper.datamodel.item.datatype.PrimitiveType
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.DataModelImporterProviderService
import uk.ac.ox.softeng.maurodatamapper.plugins.artdecor.provider.importer.parameter.ArtDecorDataModelImporterProviderServiceParameters
import uk.ac.ox.softeng.maurodatamapper.security.User

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.text.StringEscapeUtils

import java.nio.charset.Charset

@Slf4j
class ArtDecorDataModelImporterProviderService extends DataModelImporterProviderService<ArtDecorDataModelImporterProviderServiceParameters> {

    public static List<String> NON_METADATA_KEYS = ['minimumMultiplicity', 'maximumMultiplicity', 'type', 'name', 'desc', 'valueDomain', 'type']

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
    Boolean canImportMultipleDomains() {
        return true
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
        List<DataModel> imported = []
        try {
            log.debug('Parsing in file content using JsonSlurper')
            Map<String, List> result = new JsonSlurper().parseText(new String(importFile.fileContents, Charset.defaultCharset()))
            List<Map> datasets = result.datasets
            List<Map> dataset = result.dataset

            if (datasets) {
                imported = processMultiDatasets(currentUser, datasets)
            } else if (dataset) {
                imported = [processSingleDataset(currentUser, dataset.first() as Map<String, Object>)]
            }
        } catch (Exception ex) {
            throw new ApiInternalException('ART03', 'Could not import ArtDecor models', ex)
        }
        imported
    }

    private List<DataModel> processMultiDatasets(User currentUser, List<Map> datasets) {
        datasets.collect {dataset ->
            processSingleDataset(currentUser, dataset.dataset.first())
        }
    }


    private DataModel processSingleDataset(User currentUser, Map<String, Object> dataset) {

        log.debug("Importing DataModel ${name}")
        DataModel dataModel = new DataModel(label: extractValue(dataset.name),
                                            description: StringEscapeUtils.unescapeHtml3(extractValue(dataset.desc)))

        processMetadata(dataset, dataModel)

        Map<String, PrimitiveType> primitiveDataTypes = [:]
        if (dataset.concept) {
            dataset.concept.each {concept ->
                if (concept.type == 'group') {
                    processDataClass(concept as Map<String, Object>, dataModel, primitiveDataTypes)
                } else {
                    throw new ApiInternalException('ART04', 'Cannot process non-group concepts at the top level of the dataset')
                }
            }
        }
        dataModelService.checkImportedDataModelAssociations(currentUser, dataModel)
        dataModel
    }


    private DataClass processDataClass(Map<String, Object> dataClassConcept, DataModel dataModel,
                                       Map<String, PrimitiveType> primitiveDataTypes) {

        DataClass dataClass = new DataClass(
            label: extractValue(dataClassConcept.name),
            description: StringEscapeUtils.unescapeHtml3(extractValue(dataClassConcept.desc)),
            minMultiplicity: parseInt(dataClassConcept.minimumMultiplicity),
            maxMultiplicity: parseInt(dataClassConcept.maximumMultiplicity == '*' ? -1 : dataClassConcept.maximumMultiplicity)
        )
        log.debug('Created dataclass {}', dataClass.label)
        dataClassConcept.concept.each {Map concept ->
            if (concept.type == 'group') {
                DataClass childDataClass = processDataClass(concept as Map<String, Object>, dataModel, primitiveDataTypes)
                dataClass.addToDataClasses(childDataClass)
            } else if (concept.type == 'item') {
                processDataElement(concept as Map<String, Object>, dataClass, dataModel, primitiveDataTypes)
            } else {
                throw new ApiInternalException('ART05', "Unknown concept type inside DataClass ${concept.type}")
            }
        }
        processMetadata(dataClassConcept, dataClass)
        dataModel.addToDataClasses(dataClass)
        dataClass
    }

    private void processDataElement(Map<String, Object> dataElementConcept, DataClass dataClass, DataModel dataModel,
                                    Map<String, PrimitiveType> primitiveTypeMap) {

        String dataTypeLabel = extractDataTypeLabel(dataElementConcept.valueDomain[0] as Map<String, Object>)
        if (!primitiveTypeMap.containsKey(dataTypeLabel)) {
            processDataType(dataTypeLabel, dataElementConcept.valueDomain[0] as Map<String, Object>, dataModel, primitiveTypeMap)
        }

        DataElement dataElement = new DataElement(
            label: extractValue(dataElementConcept.name),
            description: StringEscapeUtils.unescapeHtml3(extractValue(dataElementConcept.desc)),
            minMultiplicity: parseInt(dataElementConcept.minimumMultiplicity),
            maxMultiplicity: parseInt(dataElementConcept.maximumMultiplicity == '*' ? -1 : dataElementConcept.maximumMultiplicity),
            dataType: primitiveTypeMap[dataTypeLabel]
        )

        processMetadata(dataElementConcept, dataElement)
        dataClass.addToDataElements(dataElement)
    }

    private void processDataType(String dataTypeLabel, Map<String, Object> dataTypeValueDomain, DataModel dataModel,
                                 Map<String, PrimitiveType> primitiveTypeMap) {
        PrimitiveType primitiveType = new PrimitiveType(label: dataTypeLabel)
        if (dataTypeValueDomain.property) processMetadata(dataTypeValueDomain.property[0] as Map<String, Object>, primitiveType)
        if (dataTypeValueDomain.conceptList) processMetadata(dataTypeValueDomain.conceptList[0] as Map<String, Object>, primitiveType)
        primitiveTypeMap[primitiveType.label] = primitiveType
        dataModel.addToDataTypes(primitiveType)
    }

    private void processMetadata(Map<String, Object> catalogueItemMap, CatalogueItem catalogueItem) {
        catalogueItemMap.each {key, value ->
            if (!(key in NON_METADATA_KEYS)) {
                String extractedValue = value instanceof String ? value : extractValue(value) ?: value.toString()
                catalogueItem.addToMetadata(namespace: namespace, key: key, value: extractedValue)
            }

        }
    }

    private static Integer parseInt(def value) {
        if (value instanceof Number) return value.toInteger()
        if (value instanceof String) {
            try {
                return value.toInteger()
            } catch (NumberFormatException ignored) {
            }
        }
        null
    }

    private static String extractValue(def information) {
        if (information instanceof List<Map<String, String>>) {
            return information.first().content ?: information.first()['#text']
        }
        null
    }

    private static String extractDataTypeLabel(Map<String, Object> dataTypeValueDomain) {
        switch (dataTypeValueDomain.type) {
            case 'code': case 'ordinal':
                return "${dataTypeValueDomain.type}_${dataTypeValueDomain.conceptList[0].id}"
            default:
                return dataTypeValueDomain.type
        }
    }
}
