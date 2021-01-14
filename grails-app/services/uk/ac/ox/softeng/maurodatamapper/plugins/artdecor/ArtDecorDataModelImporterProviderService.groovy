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

import org.springframework.beans.factory.annotation.Autowired
import uk.ac.ox.softeng.maurodatamapper.api.exception.ApiUnauthorizedException
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModel
import uk.ac.ox.softeng.maurodatamapper.datamodel.DataModelService
import uk.ac.ox.softeng.maurodatamapper.datamodel.provider.importer.DataModelImporterProviderService
import uk.ac.ox.softeng.maurodatamapper.security.User
import groovy.util.logging.Slf4j

@Slf4j
class ArtDecorDataModelImporterProviderService<T extends ArtDecorDataModelImporterProviderServiceParameters>
    extends DataModelImporterProviderService<T> {

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
    List<DataModel> importDataModels(User currentUser, T params) {
        if (!currentUser) throw new ApiUnauthorizedException('ARTDECOR01', 'User must be logged in to import model')
        log.debug("importDataModels")

        String namespace = "uk.ac.ox.softeng.maurodatamapper.plugins.artdecor"
    }

    @Override
    DataModel importDataModel(User currentUser, T params) {
        log.debug("importDataModel")
    }

    @Override
    Boolean canImportMultipleDomains() {
        true
    }    

    DataModel updateImportedModelFromParameters(DataModel dataModel, T params, boolean list = false) {
        if (params.finalised != null) dataModel.finalised = params.finalised
        if (!list && params.modelName) dataModel.label = params.modelName
        dataModel
    }    
}
